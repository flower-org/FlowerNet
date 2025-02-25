package com.flower.dns.dotclient;

import com.flower.channelpool.AggressiveChannelPool;
import com.flower.channelpool.ChannelPool;
import com.flower.dns.dotclient.viasocks.AggressiveViaSocksChannelPool;
import com.flower.utils.EmptyPipelineChannelInitializer;
import com.flower.utils.ServerUtil;
import com.flower.utils.evictlist.ConcurrentEvictListWithFixedTimeout;
import com.flower.utils.evictlist.EvictLinkedList;
import com.flower.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.SimpleChannelInboundHandler;

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.function.Consumer;

import static com.flower.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;
import static com.google.common.base.Preconditions.checkNotNull;

public class DnsOverTlsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverTlsClient.class);

    static final Long SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;
    static final Long CALLBACK_EXPIRATION_TIMEOUT = 2500L;
    static final int MAX_PARALLEL_CHANNELS = 3;
    static final int MAX_QUERY_RETRY_COUNT = 2;

    @Nullable private final InetAddress socksServerAddress;
    @Nullable private final Integer socksServerPort;
    private final InetAddress dnsServerAddress;
    private final int dnsServerPort;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;

    private final ChannelPool channelPool;
    private final EvictLinkedList<Pair<Integer, Pair<DnsQuestion, Consumer<DefaultDnsResponse>>>> callbacks;

    private final boolean useCache;
    @Nullable private final AnswerCache cache;

    private final SslContext sslCtx;

    public DnsOverTlsClient(String socksServerHost, int socksServerPort,
                                    String dnsServerHost, int dnsServerPort,
                                    TrustManagerFactory trustManager, boolean useCache) throws SSLException {
        this.socksServerAddress = ServerUtil.getByName(socksServerHost);
        this.socksServerPort = socksServerPort;

        this.dnsServerAddress = ServerUtil.getByName(dnsServerHost);
        this.dnsServerPort = dnsServerPort;

        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(CALLBACK_EXPIRATION_TIMEOUT);

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
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new EmptyPipelineChannelInitializer());

        this.channelPool = new AggressiveViaSocksChannelPool(bootstrap, socksServerAddress, socksServerPort,
                dnsServerHost, dnsServerPort,
                this,
                MAX_PARALLEL_CHANNELS);

        this.useCache = useCache;
        if (useCache) { cache = new AnswerCache(); }
                else  { cache = null; }
    }

    public DnsOverTlsClient(String dnsServerHost, int dnsServerPort, TrustManagerFactory trustManager, boolean useCache) throws SSLException {
        this.socksServerAddress = null;
        this.socksServerPort = null;
        this.dnsServerAddress = ServerUtil.getByName(dnsServerHost);
        this.dnsServerPort = dnsServerPort;
        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(CALLBACK_EXPIRATION_TIMEOUT);

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
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
                    sslHandler.setHandshakeTimeoutMillis(SSL_HANDSHAKE_TIMEOUT_MILLIS);
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
                            try {
                                handleQueryResp(msg);
                            } finally {
                                ctx.close();
                            }
                        }
                    });
                }
            });
        this.channelPool = new AggressiveChannelPool(bootstrap, dnsServerAddress, dnsServerPort, MAX_PARALLEL_CHANNELS);

        this.useCache = useCache;
        if (useCache) { cache = new AnswerCache(); }
                else  { cache = null; }
    }

    public void query(DnsQuery query, Consumer<DefaultDnsResponse> dnsResponseConsumer) {
        DnsQuestion qQuestion = query.recordAt(DnsSection.QUESTION, 0);
        if (useCache) {
            DefaultDnsResponse response = checkNotNull(cache).getResponseIfPresent(qQuestion);
            if (response != null) {
                LOGGER.info("{} | Cache hit!", query.id());
                dnsResponseConsumer.accept(response);
                return;
            }
        }

        callbacks.addElement(Pair.of(query.id(), Pair.of(qQuestion, dnsResponseConsumer)));
        query(query, 0);
    }

    protected void query(DnsQuery query, int retry) {
        if (retry > MAX_QUERY_RETRY_COUNT) {
            LOGGER.info("{} | DoT Server request failed after retry# {}", query.id(), retry);
            return;
        }

        channelPool.getChannel().addListener(channelFuture -> {
            if (channelFuture.isSuccess()) {
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

    public void shutdown() {
        group.shutdownGracefully();
    }

    public SslContext getSslCtx() {
        return sslCtx;
    }

    public void handleQueryResp(DefaultDnsResponse msg) {
        Pair<DnsQuestion, Consumer<DefaultDnsResponse>> pair = findCallback(msg.id());
        if (pair != null) {
            Consumer<DefaultDnsResponse> responseListener = pair.getValue();
            responseListener.accept(msg);
            if (useCache) {
                DnsQuestion question = pair.getKey();
                checkNotNull(cache).putResponse(question, msg);
            }
        }
    }

    protected @Nullable Pair<DnsQuestion, Consumer<DefaultDnsResponse>> findCallback(int queryId) {
        Iterator<EvictLinkedNode<Pair<Integer, Pair<DnsQuestion, Consumer<DefaultDnsResponse>>>>> iterator = callbacks.iterator();
        while (iterator.hasNext()) {
            EvictLinkedNode<Pair<Integer, Pair<DnsQuestion, Consumer<DefaultDnsResponse>>>> cursor = iterator.next();
            if (cursor.value().getKey().equals(queryId)) {
                callbacks.markEvictable(cursor);
                return cursor.value().getValue();
            }
        }
        return null;
    }
}
