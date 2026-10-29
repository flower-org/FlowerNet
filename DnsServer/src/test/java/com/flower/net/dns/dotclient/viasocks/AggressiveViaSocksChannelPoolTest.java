package com.flower.net.dns.dotclient.viasocks;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import com.flower.net.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A failed SOCKS tunnel must fail the caller and free its pool slot, rather than stay pending forever. */
@Timeout(20)
public class AggressiveViaSocksChannelPoolTest {
    static final int MAX_CHANNELS = 3;
    static final int ATTEMPTS = MAX_CHANNELS + 2;

    enum ProxyBehavior { REJECT_CONNECT, CLOSE_AFTER_GREETING, SILENT }

    NioEventLoopGroup group;
    Bootstrap bootstrap;
    DnsOverTlsClient dnsClient;
    ServerSocket proxy;
    final List<Socket> accepted = new CopyOnWriteArrayList<>();
    final AtomicInteger closedByClient = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        group = new NioEventLoopGroup(2);
        bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                    }
                });
        // Only provides the SslContext and DNS handlers; the client itself connects nowhere
        dnsClient = new DnsOverTlsClient("1.1.1.1", 853, InsecureTrustManagerFactory.INSTANCE, false, null);
        proxy = new ServerSocket(0, 100, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void tearDown() throws IOException {
        proxy.close();
        for (Socket socket : accepted) {
            socket.close();
        }
        dnsClient.shutdown();
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    }

    void startProxy(ProxyBehavior behavior) {
        Thread acceptor = new Thread(() -> {
            try {
                while (true) {
                    Socket socket = proxy.accept();
                    accepted.add(socket);
                    Thread handler = new Thread(() -> serve(socket, behavior));
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (IOException e) {
                // proxy closed
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    void serve(Socket socket, ProxyBehavior behavior) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            if (behavior == ProxyBehavior.SILENT) {
                drainUntilClientCloses(in);
                return;
            }
            // Greeting: VER, NMETHODS, METHODS
            in.readByte();
            in.readFully(new byte[in.readUnsignedByte()]);
            if (behavior == ProxyBehavior.CLOSE_AFTER_GREETING) {
                socket.close();
                return;
            }
            out.write(new byte[] {5, 0});
            out.flush();
            // CONNECT: VER, CMD, RSV, ATYP=DOMAIN, LEN, HOST, PORT
            in.readFully(new byte[4]);
            in.readFully(new byte[in.readUnsignedByte() + 2]);
            // Reply "connection refused" (0x05) and keep the socket open: the client must give up on its own
            out.write(new byte[] {5, 5, 0, 1, 0, 0, 0, 0, 0, 0});
            out.flush();
            drainUntilClientCloses(in);
        } catch (IOException e) {
            // client went away
        }
    }

    void drainUntilClientCloses(InputStream in) throws IOException {
        while (in.read() != -1) {
            // ignore
        }
        closedByClient.incrementAndGet();
    }

    AggressiveViaSocksChannelPool pool(long setupTimeoutMillis) {
        return new AggressiveViaSocksChannelPool(bootstrap, InetAddress.getLoopbackAddress(), proxy.getLocalPort(),
                "one.one.one.one", 853, dnsClient, MAX_CHANNELS, null) {
            @Override
            protected long connectionSetupTimeoutMillis() {
                return setupTimeoutMillis;
            }

            /** These tests check that failed slots are freed, so failures are retried right away. */
            @Override
            protected long initialReconnectBackoffMillis() {
                return 0;
            }
        };
    }

    /** More attempts than slots: each must fail promptly and open a new connection, since failed slots are freed. */
    List<Throwable> assertEveryAttemptFails(AggressiveViaSocksChannelPool pool) throws Exception {
        List<Throwable> causes = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            Future<EvictLinkedNode<Channel>> attempt = pool.getChannel();
            assertTrue(attempt.await(5, TimeUnit.SECONDS), "attempt " + i + " is stuck");
            assertFalse(attempt.isSuccess());
            causes.add(attempt.cause());
        }
        waitFor(() -> accepted.size() >= ATTEMPTS);
        assertEquals(ATTEMPTS, accepted.size());
        return causes;
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        while (!condition.getAsBoolean()) {
            Thread.sleep(5);
        }
    }

    @Test
    void rejectedConnectFailsWithReasonAndFreesSlot() throws Exception {
        startProxy(ProxyBehavior.REJECT_CONNECT);
        for (Throwable cause : assertEveryAttemptFails(pool(10_000))) {
            assertInstanceOf(IOException.class, cause);
            assertTrue(String.valueOf(cause.getMessage()).contains("CONNECT rejected"), String.valueOf(cause));
        }
        waitFor(() -> closedByClient.get() == ATTEMPTS);
    }

    @Test
    void proxyClosingConnectionFreesSlot() throws Exception {
        startProxy(ProxyBehavior.CLOSE_AFTER_GREETING);
        for (Throwable cause : assertEveryAttemptFails(pool(10_000))) {
            assertInstanceOf(IOException.class, cause);
        }
    }

    @Test
    void silentProxyTimesOutAndFreesSlot() throws Exception {
        startProxy(ProxyBehavior.SILENT);
        for (Throwable cause : assertEveryAttemptFails(pool(200))) {
            assertInstanceOf(TimeoutException.class, cause);
        }
        // Timed-out channels are closed by the pool
        waitFor(() -> closedByClient.get() == ATTEMPTS);
    }
}
