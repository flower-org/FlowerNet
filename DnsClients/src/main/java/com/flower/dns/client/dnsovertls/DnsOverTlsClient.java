package com.flower.dns.client.dnsovertls;

import com.flower.dns.DnsClient;
import com.flower.channelpool.AggressiveChannelPool;
import com.flower.channelpool.ChannelPool;
import com.flower.utils.PromiseUtil;
import com.flower.utils.evictlist.ConcurrentEvictListWithFixedTimeout;
import com.flower.utils.evictlist.EvictLinkedList;
import com.flower.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.SimpleChannelInboundHandler;

import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Random;

import static com.flower.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;

public class DnsOverTlsClient implements DnsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverTlsClient.class);

    public static final Long DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;
    public static final Long DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS = 2500L;
    public static final int DEFAULT_MAX_PARALLEL_CONNECTIONS = 3;
    public static final int DEFAULT_MAX_QUERY_RETRY_COUNT = 2;

    private final int maxQueryRetryCount;
    private final EvictLinkedList<Pair<Integer, Promise<DnsResponse>>> callbacks;
    private final ChannelPool channelPool;

    private final EventLoopGroup group;
    private final SslContext sslCtx;
    private final Bootstrap bootstrap;

    public DnsOverTlsClient(InetAddress dnsServerAddress, int dnsServerPort, TrustManagerFactory trustManager) throws SSLException {
        this(dnsServerAddress, dnsServerPort, trustManager, DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS,
                DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS, DEFAULT_MAX_PARALLEL_CONNECTIONS, DEFAULT_MAX_QUERY_RETRY_COUNT);
    }

    public DnsOverTlsClient(InetAddress dnsServerAddress, int dnsServerPort, TrustManagerFactory trustManager,
                            long sslHandshakeTimeoutMillis, long callbackExpirationTimeoutMillis, int maxParallelConnections,
                            int maxQueryRetryCount) throws SSLException {
        this.maxQueryRetryCount = maxQueryRetryCount;
        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(callbackExpirationTimeoutMillis);

        // Configure SSL.
        this.sslCtx = SslContextBuilder
                .forClient()
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(trustManager)
                .build();

        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioSocketChannel.class)
                //TODO: test this
//            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
                    sslHandler.setHandshakeTimeoutMillis(sslHandshakeTimeoutMillis);
                    sslHandler.handshakeFuture().addListener(
                        future -> {
                            if (!future.isSuccess()) {
                                LOGGER.error("DnsOverTlsClient - TLS Handshake Failed", future.cause());
                            }
                        }
                    );

                    p.addLast(sslHandler)
                    .addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            super.exceptionCaught(ctx, cause);
                        }
                    })
                    .addLast(new TcpDnsQueryEncoder())
                    .addLast(new TcpDnsResponseDecoder())
                    .addLast(new SimpleChannelInboundHandler<DefaultDnsResponse>() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            LOGGER.error("DnsOverTlsClient.exceptionCaught", cause);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DefaultDnsResponse msg) {
                            handleQueryResp(msg);
                        }
                    });
                }
            });
        this.channelPool = new AggressiveChannelPool(bootstrap, dnsServerAddress, dnsServerPort, maxParallelConnections);
    }

    @Override
    public void shutdown() {
        group.shutdownGracefully();
    }

    @Override
    public Promise<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        Promise<DnsResponse> promise = query(hostname);
        return PromiseUtil.withTimeout(bootstrap.config().group().next(), promise, promiseTimeoutMs);
    }

    @Override
    public Promise<DnsResponse> query(String hostname) {
        int randomID = new Random().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(hostname, DnsRecordType.A));

        Promise<DnsResponse> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
        callbacks.addElement(Pair.of(query.id(), channelPromise));
        query(query, 0);
        return channelPromise;
    }

    protected void query(DnsQuery query, int retry) {
        if (retry > maxQueryRetryCount) {
            LOGGER.info("{} | DoT Server request failed after retry# {}", query.id(), retry);
            return;
        }

        channelPool.getChannel().addListener(channelFuture -> {
            if (channelFuture.isSuccess()) {
                @SuppressWarnings("unchecked")
                EvictLinkedNode<Channel> channelRecord = (EvictLinkedNode<Channel>)channelFuture.get();
                final Channel ch = channelRecord.value();
                ch.writeAndFlush(query)
                    .addListener(
                        writeFuture -> {
                            if (!writeFuture.isSuccess()) {
                                channelPool.returnFailedChannel(channelRecord);
                                query(query, retry + 1);
                            } else {
                                channelPool.returnChannel(channelRecord);
                            }
                        }
                    );
            } else {
                LOGGER.info("{} | DoT Server request failed to get channel", query.id());
            }
        });
    }

    protected void handleQueryResp(DnsResponse msg) {
        Promise<DnsResponse> promise = findCallback(msg.id());
        if (promise != null && !promise.isDone()) { promise.setSuccess(msg); }
    }

    protected @Nullable Promise<DnsResponse> findCallback(int queryId) {
        Iterator<EvictLinkedNode<Pair<Integer, Promise<DnsResponse>>>> iterator = callbacks.iterator();
        while (iterator.hasNext()) {
            EvictLinkedNode<Pair<Integer, Promise<DnsResponse>>> cursor = iterator.next();
            if (cursor.value().getKey().equals(queryId)) {
                callbacks.markEvictable(cursor);
                return cursor.value().getValue();
            }
        }
        return null;
    }
}
