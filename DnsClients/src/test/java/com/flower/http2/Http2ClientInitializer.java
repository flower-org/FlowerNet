package com.flower.http2;

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
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Promise;

import javax.annotation.Nullable;
import java.net.InetAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {
    private final SslContext sslCtx;
    private final int maxContentLength;
    private final SimpleChannelInboundHandler<FullHttpResponse> responseHandler;
    final InetAddress address;
    final int port;
    final HttpToHttp2ConnectionHandler connectionHandler;
    @Nullable Promise<Http2Settings> settingsPromise;

    public Http2ClientInitializer(SslContext sslCtx, int maxContentLength, InetAddress address, int port,
                                  SimpleChannelInboundHandler<FullHttpResponse> responseHandler) {
        this.address = address;
        this.port = port;
        this.sslCtx = sslCtx;
        this.maxContentLength = maxContentLength;
        this.responseHandler = responseHandler;

        // TODO: why does the next line look like cancer to me?
        final Http2Connection connection = new DefaultHttp2Connection(false);
        this.connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(new DelegatingDecompressorFrameListener(
                        connection,
                        new InboundHttp2ToHttpAdapterBuilder(connection)
                                .maxContentLength(maxContentLength)
                                .propagateSettings(true)
                                .build()))
                .connection(connection)
                .build();
    }

    @Override
    public void initChannel(SocketChannel ch) {
        settingsPromise = ch.eventLoop().newPromise();
        configurePipeline(ch);
    }

    /** We must wait for the handshake to finish and the protocol to be negotiated before configuring
     *  the HTTP/2 components of the pipeline.
     */
    public @Nullable Promise<Http2Settings> settingsPromise() {
        return settingsPromise;
    }

    /**
     * Configure the pipeline for TLS NPN negotiation to HTTP/2.
     */
    private void configurePipeline(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // Specify Host in SSLContext New Handler to add TLS SNI Extension
        pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ChannelPipeline p = ctx.pipeline();
                    p.addLast(connectionHandler);
                    p.addLast(new SimpleChannelInboundHandler<Http2Settings>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) {
                            checkNotNull(settingsPromise).setSuccess(msg);
                            // Only care about the first settings message
                            ctx.pipeline().remove(this);
                        }
                    });
                    p.addLast(responseHandler);
                    return;
                }
                ctx.close();
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        });
    }
}