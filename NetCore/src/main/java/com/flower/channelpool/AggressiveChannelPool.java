package com.flower.channelpool;

import com.flower.utils.evictlist.ConcurrentEvictList;
import com.flower.utils.evictlist.EvictLinkedList;
import com.flower.utils.evictlist.EvictLinkedNode;
import com.flower.utils.evictlist.EvictListElementPicker;
import com.flower.utils.evictlist.MutableEvictLinkedNode;
import com.flower.utils.evictlist.MutableEvictNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggressively creates as many channels as it can until reaches `maxChannels`.
 * Never close the channels, evicting when they are closed by the peer.
 */
public class AggressiveChannelPool implements ChannelPool {
    protected final int maxChannels;
    protected final AtomicInteger promiseCount = new AtomicInteger();
    protected final EvictLinkedList<Channel> channels;
    protected final EvictListElementPicker<Channel> picker;

    protected final Bootstrap bootstrap;
    protected final InetAddress connectAddress;
    protected final int connectPort;

    public AggressiveChannelPool(Bootstrap bootstrap, InetAddress connectAddress, int connectPort, int maxChannels) {
        this.maxChannels = maxChannels;
        this.bootstrap = bootstrap;
        this.connectAddress = connectAddress;
        this.connectPort = connectPort;

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
        while (true) {
            if (giveChannelPromise()) {
                // 1. Aggressive approach - if we can create more channels, create more
                Promise<EvictLinkedNode<Channel>> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
                bootstrap.connect(connectAddress, connectPort).addListener(
                    (ChannelFutureListener) channelFuture -> {
                        if (channelFuture.isSuccess()) {
                            channelPromise.setSuccess(addChannel(channelFuture.channel()));
                        } else {
                            failOnChannelPromise();
                            channelPromise.setFailure(channelPromise.cause());
                        }
                    }
                );
                return channelPromise;
            } else {
                // 2. If max channels was reached, reuse
                EvictLinkedNode<Channel> nonEvictedNode = picker.getNonEvictedNode();
                if (nonEvictedNode != null) {
                    Promise<EvictLinkedNode<Channel>> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
                    channelPromise.setSuccess(nonEvictedNode);
                    return channelPromise;
                }
            }
        }
    }

    protected boolean giveChannelPromise() {
        while (true) {
            int currentPromises = promiseCount.get();
            if (channels.nonEvictedCount() + promiseCount.get() < maxChannels) {
                if (promiseCount.compareAndSet(currentPromises, currentPromises + 1)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    protected void failOnChannelPromise() {
        promiseCount.decrementAndGet();
    }

    protected EvictLinkedNode<Channel> addChannel(Channel channel) {
        promiseCount.decrementAndGet();
        return channels.addElement(channel);
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
