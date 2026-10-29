package com.flower.net.channelpool;

import com.flower.net.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
public class AggressiveChannelPoolTest {
    NioEventLoopGroup group;
    Bootstrap bootstrap;
    ServerSocket server;
    final List<Socket> accepted = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        group = new NioEventLoopGroup(2);
        bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                    }
                });
        server = new ServerSocket(0, 100, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(() -> {
            try {
                while (true) {
                    accepted.add(server.accept());
                }
            } catch (IOException e) {
                // server closed
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
        for (Socket socket : accepted) {
            socket.close();
        }
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    }

    /** Pool whose connections stay pending until the test completes their setup. */
    static class ManualSetupPool extends AggressiveChannelPool {
        final List<Promise<Void>> setups = new CopyOnWriteArrayList<>();
        final List<Channel> setupChannels = new CopyOnWriteArrayList<>();
        final long setupTimeoutMillis;

        ManualSetupPool(Bootstrap bootstrap, int port, int maxChannels) {
            this(bootstrap, port, maxChannels, DEFAULT_CONNECTION_SETUP_TIMEOUT_MILLIS);
        }

        ManualSetupPool(Bootstrap bootstrap, int port, int maxChannels, long setupTimeoutMillis) {
            super(bootstrap, InetAddress.getLoopbackAddress(), port, maxChannels, null);
            this.setupTimeoutMillis = setupTimeoutMillis;
        }

        @Override
        protected Future<?> onConnected(Channel channel) {
            Promise<Void> setup = channel.eventLoop().newPromise();
            setupChannels.add(channel);
            setups.add(setup);
            return setup;
        }

        @Override
        protected long connectionSetupTimeoutMillis() {
            return setupTimeoutMillis;
        }

        /** These tests check slot accounting, so failures are retried right away. */
        @Override
        protected long initialReconnectBackoffMillis() {
            return 0;
        }
    }

    /** Pool with a short, observable backoff. */
    static class BackoffPool extends AggressiveChannelPool {
        BackoffPool(Bootstrap bootstrap, int port, int maxChannels) {
            super(bootstrap, InetAddress.getLoopbackAddress(), port, maxChannels, null);
        }

        @Override
        protected long initialReconnectBackoffMillis() {
            return 200;
        }

        @Override
        protected long maxReconnectBackoffMillis() {
            return 400;
        }
    }

    static int closedPort() throws IOException {
        try (ServerSocket unused = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return unused.getLocalPort();
        }
    }

    static boolean isBackoff(Future<?> future) {
        return future.isDone() && future.cause() instanceof ConnectException
                && future.cause().getMessage().startsWith("Not connecting");
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        while (!condition.getAsBoolean()) {
            Thread.sleep(5);
        }
    }

    /** Accepts happen asynchronously: wait for the expected count, then make sure no extra connections show up. */
    void assertAcceptedConnections(int expected) throws InterruptedException {
        waitFor(() -> accepted.size() >= expected);
        Thread.sleep(100);
        assertEquals(expected, accepted.size());
    }

    @Test
    void waitsForPendingConnectionsInsteadOfSpinning() throws Exception {
        ManualSetupPool pool = new ManualSetupPool(bootstrap, server.getLocalPort(), 2);
        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        Future<EvictLinkedNode<Channel>> second = pool.getChannel();

        // Both slots are pending: further calls return immediately, sharing the pending connections
        List<Future<EvictLinkedNode<Channel>>> waiting = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<EvictLinkedNode<Channel>> future = pool.getChannel();
            assertTrue(future == first || future == second);
            assertFalse(future.isDone());
            waiting.add(future);
        }

        waitFor(() -> pool.setups.size() == 2);
        for (Promise<Void> setup : pool.setups) {
            setup.setSuccess(null);
        }
        for (Future<EvictLinkedNode<Channel>> future : waiting) {
            assertTrue(future.await(5, TimeUnit.SECONDS));
            assertTrue(future.isSuccess());
        }
        assertAcceptedConnections(2);

        // Pool is full of established channels: they are reused
        Set<Channel> reused = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Future<EvictLinkedNode<Channel>> future = pool.getChannel();
            assertTrue(future.isSuccess());
            reused.add(future.getNow().value());
        }
        assertEquals(Set.of(first.getNow().value(), second.getNow().value()), reused);
        assertAcceptedConnections(2);
    }

    @Test
    void neverExceedsMaxChannelsUnderConcurrency() throws Exception {
        AggressiveChannelPool pool = new AggressiveChannelPool(
                bootstrap, InetAddress.getLoopbackAddress(), server.getLocalPort(), 3, null);
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EvictLinkedNode<Channel>>> futures = new CopyOnWriteArrayList<>();
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        futures.add(pool.getChannel());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        Set<Channel> channels = new HashSet<>();
        for (Future<EvictLinkedNode<Channel>> future : futures) {
            assertTrue(future.await(5, TimeUnit.SECONDS));
            assertTrue(future.isSuccess());
            channels.add(future.getNow().value());
        }
        assertEquals(3, channels.size());
        assertAcceptedConnections(3);
    }

    @Test
    void failedConnectionReportsCauseAndBacksOff() throws Exception {
        BackoffPool pool = new BackoffPool(bootstrap, closedPort(), 1);

        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        assertTrue(first.await(5, TimeUnit.SECONDS));
        assertInstanceOf(ConnectException.class, first.cause());
        assertFalse(isBackoff(first));

        // Right after the failure, callers fail fast without a new connection attempt
        Future<EvictLinkedNode<Channel>> second = pool.getChannel();
        assertTrue(isBackoff(second), String.valueOf(second.cause()));

        // Once the backoff ends, the slot is free for a new attempt
        Thread.sleep(250);
        Future<EvictLinkedNode<Channel>> third = pool.getChannel();
        assertTrue(third.await(5, TimeUnit.SECONDS));
        assertFalse(isBackoff(third));

        // The second failure doubles the backoff, up to the max
        assertEquals(200, pool.reconnectBackoffMillis(1));
        assertEquals(400, pool.reconnectBackoffMillis(2));
        assertEquals(400, pool.reconnectBackoffMillis(40));
        Thread.sleep(250);
        assertTrue(isBackoff(pool.getChannel()));
        Thread.sleep(200);
        assertFalse(isBackoff(pool.getChannel()));
    }

    @Test
    void establishedChannelsAreUsedDuringBackoffAndSuccessResetsIt() throws Exception {
        ManualSetupPool pool = new ManualSetupPool(bootstrap, server.getLocalPort(), 2) {
            @Override
            protected long initialReconnectBackoffMillis() {
                return 300;
            }
        };
        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        waitFor(() -> pool.setups.size() == 1);
        pool.setups.get(0).setSuccess(null);
        assertTrue(first.await(5, TimeUnit.SECONDS));

        // A second connection fails: the pool has a free slot but doesn't try it while backing off
        Future<EvictLinkedNode<Channel>> failing = pool.getChannel();
        waitFor(() -> pool.setups.size() == 2);
        pool.setups.get(1).setFailure(new RuntimeException("handshake failed"));
        assertTrue(failing.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 5; i++) {
            Future<EvictLinkedNode<Channel>> reused = pool.getChannel();
            assertTrue(reused.isSuccess());
            assertSame(first.getNow().value(), reused.getNow().value());
        }
        assertEquals(2, pool.setups.size());

        // After the backoff a new connection is made, and its success resets the backoff
        Thread.sleep(350);
        Future<EvictLinkedNode<Channel>> next = pool.getChannel();
        waitFor(() -> pool.setups.size() == 3);
        pool.setups.get(2).setSuccess(null);
        assertTrue(next.await(5, TimeUnit.SECONDS));
        assertTrue(next.isSuccess());
        first.getNow().value().close().sync();
        next.getNow().value().close().sync();
        Future<EvictLinkedNode<Channel>> afterReset = pool.getChannel();
        assertFalse(isBackoff(afterReset));
        waitFor(() -> pool.setups.size() == 4);
    }

    @Test
    void failedSetupClosesChannelAndFreesSlot() throws Exception {
        ManualSetupPool pool = new ManualSetupPool(bootstrap, server.getLocalPort(), 1);
        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        waitFor(() -> pool.setups.size() == 1);
        RuntimeException cause = new RuntimeException("handshake failed");
        pool.setups.get(0).setFailure(cause);
        assertTrue(first.await(5, TimeUnit.SECONDS));
        assertSame(cause, first.cause());

        Future<EvictLinkedNode<Channel>> second = pool.getChannel();
        assertTrue(second != first);
        waitFor(() -> pool.setups.size() == 2);
        pool.setups.get(1).setSuccess(null);
        assertTrue(second.await(5, TimeUnit.SECONDS));
        assertTrue(second.isSuccess());
        assertTrue(second.getNow().value().isActive());
    }

    @Test
    void channelClosedDuringSetupFreesSlot() throws Exception {
        ManualSetupPool pool = new ManualSetupPool(bootstrap, server.getLocalPort(), 1);
        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        waitFor(() -> pool.setups.size() == 1);
        // Setup never completes, e.g. a proxy handler that closes the channel without failing its future
        pool.setupChannels.get(0).close();
        assertTrue(first.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, first.cause());

        Future<EvictLinkedNode<Channel>> second = pool.getChannel();
        assertTrue(second != first);
        waitFor(() -> pool.setups.size() == 2);
    }

    @Test
    void setupTimeoutClosesChannelAndFreesSlot() throws Exception {
        ManualSetupPool pool = new ManualSetupPool(bootstrap, server.getLocalPort(), 1, 100);
        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        assertTrue(first.await(5, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, first.cause());
        assertTrue(pool.setupChannels.get(0).closeFuture().await(5, TimeUnit.SECONDS));

        Future<EvictLinkedNode<Channel>> second = pool.getChannel();
        assertTrue(second != first);
        waitFor(() -> pool.setups.size() == 2);
        pool.setups.get(1).setSuccess(null);
        assertTrue(second.await(5, TimeUnit.SECONDS));
        assertTrue(second.isSuccess());
    }

    @Test
    void closedChannelIsReplaced() throws Exception {
        AggressiveChannelPool pool = new AggressiveChannelPool(
                bootstrap, InetAddress.getLoopbackAddress(), server.getLocalPort(), 1, null);
        Future<EvictLinkedNode<Channel>> first = pool.getChannel();
        assertTrue(first.await(5, TimeUnit.SECONDS));
        Channel firstChannel = first.getNow().value();
        firstChannel.close().sync();

        Future<EvictLinkedNode<Channel>> second = pool.getChannel();
        assertTrue(second.await(5, TimeUnit.SECONDS));
        assertTrue(second.isSuccess());
        assertTrue(second.getNow().value() != firstChannel);
        assertTrue(second.getNow().value().isActive());
    }
}
