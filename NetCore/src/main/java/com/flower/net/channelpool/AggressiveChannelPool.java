package com.flower.net.channelpool;

import com.flower.net.utils.evictlist.ConcurrentEvictList;
import com.flower.net.utils.evictlist.EvictLinkedList;
import com.flower.net.utils.evictlist.EvictLinkedNode;
import com.flower.net.utils.evictlist.EvictListElementPicker;
import com.flower.net.utils.evictlist.MutableEvictLinkedNode;
import com.flower.net.utils.evictlist.MutableEvictNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aggressively creates as many channels as it can until reaches `maxChannels`.
 * Never close the channels, evicting when they are closed by the peer.
 * When all `maxChannels` slots are taken by connections still being established, callers wait for one of them
 * instead of busy-waiting.
 */
public class AggressiveChannelPool implements ChannelPool {
    public static final long DEFAULT_CONNECTION_SETUP_TIMEOUT_MILLIS = 10_000;

    protected final int maxChannels;
    protected final EvictLinkedList<Channel> channels;
    protected final EvictListElementPicker<Channel> picker;

    protected final Bootstrap bootstrap;
    protected final InetAddress connectAddress;
    protected final int connectPort;
    @Nullable protected final String bindClientToIp;

    /** Guards slot accounting: non-evicted channels + pending connections must not exceed maxChannels. */
    private final Object slotLock = new Object();
    private final List<Promise<EvictLinkedNode<Channel>>> pendingConnections = new ArrayList<>();
    private int nextPendingConnection;

    public AggressiveChannelPool(Bootstrap bootstrap, InetAddress connectAddress, int connectPort, int maxChannels, @Nullable String bindClientToIp) {
        this.maxChannels = maxChannels;
        this.bootstrap = bootstrap;
        this.connectAddress = connectAddress;
        this.connectPort = connectPort;
        this.bindClientToIp = bindClientToIp;

        // Custom eviction list for channels
        this.channels = new ConcurrentEvictList<>() {
            @Override
            protected MutableEvictLinkedNode<Channel> createMutableEvictLinkedNode(Channel value) {
            return new MutableEvictNode<>(value) {
                @Override
                public boolean isEvicted() {
                    if (!super.isEvicted() && !value.isActive()) {
                        markForEviction();
                    }
                    return super.isEvicted();
                }
            };
            }
        };
        this.picker = channels.newElementPicker();
    }

    @Override
    public Promise<EvictLinkedNode<Channel>> getChannel() {
        Promise<EvictLinkedNode<Channel>> newConnection;
        synchronized (slotLock) {
            if (channels.nonEvictedCount() + pendingConnections.size() >= maxChannels) {
                // 1. Max channels reached - reuse an established channel
                EvictLinkedNode<Channel> nonEvictedNode = picker.getNonEvictedNode();
                if (nonEvictedNode != null) {
                    Promise<EvictLinkedNode<Channel>> channelPromise = newPromise();
                    channelPromise.setSuccess(nonEvictedNode);
                    return channelPromise;
                }
                // 2. All slots are connections still being established - wait for one of them
                if (!pendingConnections.isEmpty()) {
                    nextPendingConnection = (nextPendingConnection + 1) % pendingConnections.size();
                    return pendingConnections.get(nextPendingConnection);
                }
                // Channels became inactive since they were counted, so slots are free
            }
            // 3. Aggressive approach - if we can create more channels, create more
            newConnection = newPromise();
            pendingConnections.add(newConnection);
        }
        connect(newConnection);
        return newConnection;
    }

    protected Promise<EvictLinkedNode<Channel>> newPromise() {
        return bootstrap.config().group().next().newPromise();
    }

    protected void connect(Promise<EvictLinkedNode<Channel>> connectionPromise) {
        ChannelFuture connectFuture;
        if (bindClientToIp == null) {
            connectFuture = bootstrap.connect(connectAddress, connectPort);
        } else {
            connectFuture = bootstrap.connect(new InetSocketAddress(connectAddress, connectPort),
                    new InetSocketAddress(bindClientToIp, 0));
        }
        connectFuture.addListener(connected -> {
            if (!connected.isSuccess()) {
                failConnection(connectionPromise, connected.cause());
                return;
            }
            Channel channel = connectFuture.channel();
            Future<?> ready;
            try {
                ready = onConnected(channel);
            } catch (Exception e) {
                channel.close();
                failConnection(connectionPromise, e);
                return;
            }
            awaitSetup(channel, ready).addListener(setupFuture -> {
                if (setupFuture.isSuccess()) {
                    completeConnection(connectionPromise, channel);
                } else {
                    channel.close();
                    failConnection(connectionPromise, setupFuture.cause());
                }
            });
        });
    }

    /**
     * Completes when `ready` does, but fails early if the channel closes or setup takes too long,
     * so that a setup that never completes can't hold a pending slot forever.
     */
    private Future<Void> awaitSetup(Channel channel, Future<?> ready) {
        Promise<Void> setup = channel.eventLoop().newPromise();
        ready.addListener(readyFuture -> {
            if (readyFuture.isSuccess()) {
                setup.trySuccess(null);
            } else {
                setup.tryFailure(readyFuture.cause());
            }
        });
        channel.closeFuture().addListener(closed -> setup.tryFailure(
                new IOException("Channel to " + connectAddress + ":" + connectPort + " closed during connection setup")));
        long timeoutMillis = connectionSetupTimeoutMillis();
        ScheduledFuture<?> timeout = channel.eventLoop().schedule(() -> {
                setup.tryFailure(new TimeoutException("Connection setup to " + connectAddress + ":" + connectPort
                        + " timed out after " + timeoutMillis + "ms"));
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        setup.addListener(done -> timeout.cancel(false));
        return setup;
    }

    /** Max time between TCP connect and the future returned by {@link #onConnected} completing. */
    protected long connectionSetupTimeoutMillis() {
        return DEFAULT_CONNECTION_SETUP_TIMEOUT_MILLIS;
    }

    /**
     * Called once the TCP connection is established. Subclasses can perform additional setup, e.g. a proxy handshake;
     * the channel is added to the pool when the returned future succeeds, and closed if it fails, if the channel
     * closes first, or if it doesn't complete within {@link #connectionSetupTimeoutMillis()}.
     */
    protected Future<?> onConnected(Channel channel) {
        return channel.newSucceededFuture();
    }

    private void completeConnection(Promise<EvictLinkedNode<Channel>> connectionPromise, Channel channel) {
        EvictLinkedNode<Channel> node;
        synchronized (slotLock) {
            // Add the channel before releasing the pending slot, so the slot count never dips below the actual count
            node = channels.addElement(channel);
            pendingConnections.remove(connectionPromise);
        }
        connectionPromise.setSuccess(node);
    }

    private void failConnection(Promise<EvictLinkedNode<Channel>> connectionPromise, @Nullable Throwable cause) {
        synchronized (slotLock) {
            pendingConnections.remove(connectionPromise);
        }
        connectionPromise.setFailure(cause != null ? cause : new Exception("Connection to " + connectAddress + ":" + connectPort + " failed"));
    }

    @Override
    public void returnChannel(EvictLinkedNode<Channel> channelNode) {
        // no-op in this implementation, we allow simultaneous use of channels and don't keep track of who owns what
    }

    @Override
    public void returnFailedChannel(EvictLinkedNode<Channel> channelNode) {
        channels.markEvictable(channelNode);
    }
}
