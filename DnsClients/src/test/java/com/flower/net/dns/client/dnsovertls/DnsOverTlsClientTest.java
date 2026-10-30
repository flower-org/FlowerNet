package com.flower.net.dns.client.dnsovertls;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the adapter against a fake DoT upstream that answers hostN.test with 10.0.0.N and anything else NXDOMAIN. */
@Timeout(30)
class DnsOverTlsClientTest {
    NioEventLoopGroup serverGroup;
    SelfSignedCertificate cert;
    DnsOverTlsClient client;
    volatile boolean answer = true;

    @BeforeEach
    void setUp() throws Exception {
        serverGroup = new NioEventLoopGroup(1);
        cert = new SelfSignedCertificate();
        client = new DnsOverTlsClient(InetAddress.getLoopbackAddress(), startUpstream(),
                InsecureTrustManagerFactory.INSTANCE, null);
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
        serverGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        cert.delete();
    }

    int startUpstream() throws Exception {
        SslContext sslCtx = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .protocols(TLS_PROTOCOLS).ciphers(TLS_CIPHERS).build();
        Channel server = new ServerBootstrap().group(serverGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()))
                                .addLast(new TcpDnsQueryDecoder())
                                .addLast(new TcpDnsResponseEncoder())
                                .addLast(new SimpleChannelInboundHandler<DnsQuery>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DnsQuery query) {
                                        if (answer) {
                                            ctx.writeAndFlush(respond(query));
                                        }
                                    }
                                });
                    }
                })
                .bind(InetAddress.getLoopbackAddress(), 0).sync().channel();
        return ((InetSocketAddress) server.localAddress()).getPort();
    }

    static DefaultDnsResponse respond(DnsQuery query) {
        DnsRecord q = query.recordAt(DnsSection.QUESTION, 0);
        String name = q.name();
        DefaultDnsResponse response = new DefaultDnsResponse(query.id(), DnsOpCode.QUERY);
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, q.type()));
        if (name.matches("host\\d+\\.test\\.")) {
            int n = Integer.parseInt(name.substring(4, name.indexOf('.')));
            response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(name, DnsRecordType.A, 60,
                    Unpooled.wrappedBuffer(new byte[] {10, 0, 0, (byte) n})));
        } else {
            response.setCode(DnsResponseCode.NXDOMAIN);
        }
        return response;
    }

    static byte[] firstAddress(DnsResponse response) {
        DnsRawRecord record = response.recordAt(DnsSection.ANSWER, 0);
        return ByteBufUtil.getBytes(record.content());
    }

    @Test
    void resolvesAndResponseOutlivesTheConnection() throws Exception {
        Future<DnsResponse> future = client.query("host7.test", 5000).await();
        assertTrue(future.isSuccess(), String.valueOf(future.cause()));
        // Read later, on another thread: the response must not depend on the channel's buffers
        Thread.sleep(200);
        DnsResponse response = future.getNow();
        assertEquals(DnsResponseCode.NOERROR, response.code());
        assertArrayEquals(new byte[] {10, 0, 0, 7}, firstAddress(response));
        assertEquals(1, response.refCnt());
    }

    @Test
    void concurrentQueriesGetTheirOwnAnswers() throws Exception {
        List<Future<DnsResponse>> futures = new ArrayList<>();
        for (int n = 1; n <= 50; n++) {
            futures.add(client.query("host" + n + ".test", 5000));
        }
        for (int n = 1; n <= 50; n++) {
            Future<DnsResponse> future = futures.get(n - 1).await();
            assertTrue(future.isSuccess(), String.valueOf(future.cause()));
            assertArrayEquals(new byte[] {10, 0, 0, (byte) n}, firstAddress(future.getNow()));
        }
    }

    @Test
    void errorResponsesAreReturned() throws Exception {
        Future<DnsResponse> future = client.query("missing.test", 5000).await();
        assertTrue(future.isSuccess(), String.valueOf(future.cause()));
        assertEquals(DnsResponseCode.NXDOMAIN, future.getNow().code());
        assertEquals(0, future.getNow().count(DnsSection.ANSWER));
    }

    @Test
    void timesOutWhenUpstreamIsSilent() throws Exception {
        answer = false;
        Future<DnsResponse> future = client.query("host1.test", 300).await();
        assertInstanceOf(TimeoutException.class, future.cause());
    }

    @Test
    void untimedQueryStillCompletesWhenUpstreamIsSilent() throws Exception {
        answer = false;
        Future<DnsResponse> future = client.query("host1.test");
        assertTrue(future.await(10, TimeUnit.SECONDS), "never completed");
        assertEquals(DnsResponseCode.SERVFAIL, future.getNow().code());
    }
}
