package com.flower.net.dns.dotclient;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client must always get an answer: the upstream response, or SERVFAIL when upstream fails. */
@Timeout(30)
public class DnsOverTlsClientFailureTest {
    static final DnsQuestion QUESTION = new DefaultDnsQuestion("example.org.", DnsRecordType.A);

    NioEventLoopGroup serverGroup;
    SelfSignedCertificate cert;
    DnsOverTlsClient client;
    /** Number of queries the fake upstream ignores before it starts answering; -1 to never answer. */
    volatile int queriesToIgnore;
    final AtomicInteger queriesReceived = new AtomicInteger();
    long answerMillis;

    @BeforeEach
    void setUp() throws Exception {
        serverGroup = new NioEventLoopGroup(1);
        cert = new SelfSignedCertificate();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
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
                                        int n = queriesReceived.incrementAndGet();
                                        if (queriesToIgnore < 0 || n <= queriesToIgnore) {
                                            return;
                                        }
                                        DnsRecord q = query.recordAt(DnsSection.QUESTION, 0);
                                        DefaultDnsResponse response = new DefaultDnsResponse(query.id(), DnsOpCode.QUERY);
                                        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(q.name(), q.type()));
                                        response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(q.name(),
                                                DnsRecordType.A, 60, Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4})));
                                        ctx.writeAndFlush(response);
                                    }
                                });
                    }
                })
                .bind(InetAddress.getLoopbackAddress(), 0).sync().channel();
        return ((java.net.InetSocketAddress) server.localAddress()).getPort();
    }

    DnsOverTlsClient client(int port) throws Exception {
        client = new DnsOverTlsClient(InetAddress.getLoopbackAddress().getHostAddress(), port,
                InsecureTrustManagerFactory.INSTANCE, false, null);
        return client;
    }

    /** Response is only valid during the callback, so the test reads its fields there. */
    record Answer(int id, String questionName, DnsResponseCode code) { }

    Answer query(DnsOverTlsClient client) throws Exception {
        DefaultDnsQuery query = new DefaultDnsQuery(4242);
        query.addRecord(DnsSection.QUESTION, QUESTION);
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        long start = System.nanoTime();
        AtomicInteger callbacks = new AtomicInteger();
        client.query(query, response -> {
            callbacks.incrementAndGet();
            answer.complete(new Answer(response.id(),
                    response.<DnsQuestion>recordAt(DnsSection.QUESTION).name(), response.code()));
        });
        Answer response = answer.get(10, TimeUnit.SECONDS);
        answerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertEquals(4242, response.id());
        assertEquals(QUESTION.name(), response.questionName());
        // Late events (retries, the deadline) must not answer a second time
        Thread.sleep(DnsOverTlsClient.CALLBACK_EXPIRATION_TIMEOUT - DnsOverTlsClient.QUERY_ATTEMPT_TIMEOUT_MILLIS);
        assertEquals(1, callbacks.get());
        return response;
    }

    @Test
    void answersNormally() throws Exception {
        Answer response = query(client(startUpstream()));
        assertEquals(DnsResponseCode.NOERROR, response.code());
        assertEquals(1, queriesReceived.get());
        assertEquals(0, client.pendingQueries().inFlightCount());
    }

    @Test
    void retriesWhenNoResponseArrives() throws Exception {
        queriesToIgnore = 1;
        Answer response = query(client(startUpstream()));
        assertEquals(DnsResponseCode.NOERROR, response.code());
        assertEquals(2, queriesReceived.get());
        assertTrue(answerMillis >= DnsOverTlsClient.QUERY_ATTEMPT_TIMEOUT_MILLIS, answerMillis + "ms");
    }

    @Test
    void servFailWhenUpstreamNeverAnswers() throws Exception {
        queriesToIgnore = -1;
        Answer response = query(client(startUpstream()));
        assertEquals(DnsResponseCode.SERVFAIL, response.code());
        assertEquals(DnsOverTlsClient.MAX_QUERY_RETRY_COUNT + 1, queriesReceived.get());
        assertEquals(0, client.pendingQueries().inFlightCount());
    }

    @Test
    void servFailWhenUpstreamIsUnreachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        Answer response = query(client(closedPort));
        assertEquals(DnsResponseCode.SERVFAIL, response.code());
        // Connection failures are retried right away, so the client isn't kept waiting for the timeouts
        assertTrue(answerMillis < DnsOverTlsClient.QUERY_ATTEMPT_TIMEOUT_MILLIS, answerMillis + "ms");
        assertEquals(0, client.pendingQueries().inFlightCount());
    }
}
