package com.flower.dns;

import io.netty.bootstrap.Bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.function.Consumer;

public final class DnsOverTlsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverTlsClient.class);

    static final Long SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;

    static final AttributeKey<Consumer<DefaultDnsResponse>> RESPONSE_CONSUMER_ATTR =
            AttributeKey.valueOf("response_consumer");

    private final InetAddress dnsServerAddress;
    private final int dnsServerPort;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;

    public DnsOverTlsClient(InetAddress dnsServerAddress, int dnsServerPort, TrustManagerFactory trustManager) throws SSLException {
        this.dnsServerAddress = dnsServerAddress;
        this.dnsServerPort = dnsServerPort;

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
                                handleQueryResp(msg, ctx.channel());
                            } finally {
                                ctx.close();
                            }
                        }
                    });
                }
            });
    }

    public void query(DnsQuery query, Consumer<DefaultDnsResponse> dnsResponseConsumer) {
        bootstrap.connect(dnsServerAddress, dnsServerPort).addListener((ChannelFutureListener) channelFuture -> {
            final Channel ch = channelFuture.channel();
            ch.attr(RESPONSE_CONSUMER_ATTR).set(dnsResponseConsumer);
            ch.writeAndFlush(query);
        });
    }

    public void shutdown() {
        group.shutdownGracefully();
    }

    private void handleQueryResp(DefaultDnsResponse msg, Channel channel) {
        Consumer<DefaultDnsResponse> responseListener = channel.attr(RESPONSE_CONSUMER_ATTR).get();
        responseListener.accept(msg);
    }
}
