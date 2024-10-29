package com.flower.dns.dotclient;

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

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.function.Consumer;

public final class DnsOverTlsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverTlsClient.class);

    static final Long SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;
    static final Long CALLBACK_EXPIRATION_TIMEOUT = 2500L;
    static final int MAX_PARALLEL_CHANNELS = 3;
    static final int MAX_QUERY_RETRY_COUNT = 2;

    static final AttributeKey<EvictLinkedNode<Channel>> CHANNEL_RECORD_ATTR =
            AttributeKey.valueOf("response_consumer");

    private final InetAddress dnsServerAddress;
    private final int dnsServerPort;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;

    private final ChannelPool channelPool;
    private final EvictLinkedList<Pair<Integer, Consumer<DefaultDnsResponse>>> callbacks;

    public DnsOverTlsClient(InetAddress dnsServerAddress, int dnsServerPort, TrustManagerFactory trustManager) throws SSLException {
        this.dnsServerAddress = dnsServerAddress;
        this.dnsServerPort = dnsServerPort;
        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(CALLBACK_EXPIRATION_TIMEOUT);

        // Configure SSL.
        final SslContext sslCtx = SslContextBuilder
                .forClient()
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
                                LOGGER.error("SSL Handshake Failed", future.cause());
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
/*
                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            if (ctx.channel().hasAttr(CHANNEL_RECORD_ATTR)) {
                                channelPool.channelInactive(ctx.channel().attr(CHANNEL_RECORD_ATTR).get());
                            }
                            super.channelInactive(ctx);
                        }*/
                    });
                }
            });
        this.channelPool = new AggressiveChannelPool(bootstrap, dnsServerAddress, dnsServerPort, MAX_PARALLEL_CHANNELS);
    }

    public void query(DnsQuery query, Consumer<DefaultDnsResponse> dnsResponseConsumer) {
        callbacks.addElement(Pair.of(query.id(), dnsResponseConsumer));
        query(query, 0);
    }

    public void query(DnsQuery query, int retry) {
        if (retry > MAX_QUERY_RETRY_COUNT) { return; }

        channelPool.getChannel().addListener(channelFuture -> {
            if (channelFuture.isSuccess()) {
                EvictLinkedNode<Channel> channelRecord = (EvictLinkedNode<Channel>)channelFuture.get();
                final Channel ch = channelRecord.value();
                ch.attr(CHANNEL_RECORD_ATTR).set(channelRecord);

                ch.writeAndFlush(query)
                    .addListener(
                        writeFuture -> {
                            if (!writeFuture.isSuccess()) {
                                LOGGER.info("{} | WRITE FUTURE UNSUCCESS retry# {}", query.id(), retry);
                                channelPool.returnFailedChannel(channelRecord);
                                query(query, retry + 1);
                            } else {
                                LOGGER.info("{} | WRITE FUTURE SUCCESS", query.id());
                                channelPool.returnChannel(channelRecord);
                            }
                        }
                    );
            } else {
                LOGGER.info("{} | FAILED TO GET CHANNEL", query.id());
            }
        });
    }

    public void shutdown() {
        group.shutdownGracefully();
    }

    private void handleQueryResp(DefaultDnsResponse msg) {
        Consumer<DefaultDnsResponse> responseListener = findCallback(msg.id());
        if (responseListener != null) {
            responseListener.accept(msg);
        }
    }

    private @Nullable Consumer<DefaultDnsResponse> findCallback(int queryId) {
        EvictLinkedNode<Pair<Integer, Consumer<DefaultDnsResponse>>> cursor = callbacks.root();
        while (cursor != null) {
            if (cursor.value().getKey() == queryId) {
                callbacks.markEvictable(cursor);
                return cursor.value().getValue();
            }
            cursor = cursor.next();
        }
        return null;
    }
}
