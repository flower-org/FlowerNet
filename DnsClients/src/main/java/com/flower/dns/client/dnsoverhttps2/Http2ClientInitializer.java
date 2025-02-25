package com.flower.dns.client.dnsoverhttps2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.util.function.Supplier;

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {
    public static final AttributeKey<Promise<Http2Settings>> SETTINGS_FUTURE_KEY =
            AttributeKey.valueOf("settings_future");

    private final SslContext sslCtx;
    private final int maxContentLength;
    private final long sslHandshakeTimeoutMillis;
    private final Supplier<SimpleChannelInboundHandler<FullHttpResponse>> responseHandlerFactory;
    final InetAddress address;
    final int port;

    public Http2ClientInitializer(SslContext sslCtx, int maxContentLength, long sslHandshakeTimeoutMillis,
                                  InetAddress address, int port,
                                  Supplier<SimpleChannelInboundHandler<FullHttpResponse>> responseHandlerFactory) {
        this.address = address;
        this.port = port;
        this.sslCtx = sslCtx;
        this.maxContentLength = maxContentLength;
        this.sslHandshakeTimeoutMillis = sslHandshakeTimeoutMillis;
        this.responseHandlerFactory = responseHandlerFactory;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ch.attr(SETTINGS_FUTURE_KEY).set(ch.eventLoop().newPromise());
        configurePipeline(ch);
    }

    /**
     * Configure the pipeline for TLS NPN negotiation to HTTP/2.
     */
    private void configurePipeline(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // Specify Host in SSLContext New Handler to add TLS SNI Extension
        SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
        sslHandler.setHandshakeTimeoutMillis(sslHandshakeTimeoutMillis);
        sslHandler.handshakeFuture().addListener(
                future -> {
                    if (!future.isSuccess()) {
                        ch.attr(SETTINGS_FUTURE_KEY).get().setFailure(future.cause());
                    }
                }
        );

        pipeline.addLast(sslHandler);
        pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ChannelPipeline p = ctx.pipeline();
                    final Http2Connection connection = new DefaultHttp2Connection(false);
                    p.addLast(new HttpToHttp2ConnectionHandlerBuilder()
                            .frameListener(new DelegatingDecompressorFrameListener(
                                    connection,
                                    new InboundHttp2ToHttpAdapterBuilder(connection)
                                            .maxContentLength(maxContentLength)
                                            .propagateSettings(true)
                                            .build()))
                            .connection(connection)
                            .build());
                    p.addLast(new SimpleChannelInboundHandler<Http2Settings>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) {
                            ctx.channel().attr(SETTINGS_FUTURE_KEY).get().setSuccess(msg);
                            // Only care about the first settings message
                            ctx.pipeline().remove(this);
                        }
                    });
                    p.addLast(responseHandlerFactory.get());
//                    p.addLast(new LoggingHandler(LogLevel.DEBUG));
                    return;
                }
                ctx.close();
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        });
    }
}