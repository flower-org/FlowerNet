package com.flower.net.visitor.client;

import com.flower.crypt.PkiUtil;
import com.flower.net.visitor.cells.TorCell;
import com.flower.net.visitor.cells.VersionsTorCell;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import java.net.InetAddress;
import java.security.cert.X509Certificate;

import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;

public class TorClientV3 implements TorClient {
    static final Logger LOGGER = LoggerFactory.getLogger(TorClientV3.class);

    private final EventLoopGroup group;
    private final SslContext sslCtx;
    private final Bootstrap bootstrap;

    public TorClientV3(TrustManagerFactory trustManager, long sslHandshakeTimeoutMillis) throws SSLException {
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
            //TODO: try this
            //.option(ChannelOption.SO_KEEPALIVE, true)
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
                            } else {
                                // TODO: TLS handshake listener - validate certificate!
                                LOGGER.info("TLS Handshake succeeded");

                                // Retrieve the SSL session
                                SSLSession sslSession = sslHandler.engine().getSession();
                                try {
                                    // Get the peer certificates
                                    X509Certificate[] peerCertificates = (X509Certificate[]) sslSession.getPeerCertificates();

                                    // Validate the certificate (you can implement your own validation logic here)
                                    for (X509Certificate cert : peerCertificates) {
                                        LOGGER.info("Received certificate: " + cert.getSubjectDN());
                                        // You can add your validation logic here
                                        LOGGER.info(PkiUtil.getCertificateAsPem(cert));
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Failed to retrieve peer certificates", e);
                                }
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
                        .addLast(new TorEncoder())
                        .addLast(new TorDecoder())
                        .addLast(new SimpleChannelInboundHandler<TorCell>() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                LOGGER.error("TorClientV3.exceptionCaught", cause);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, TorCell msg) {
                                handleQueryResp(msg);
                            }
                        });
                }
            });
    }

    protected void handleQueryResp(TorCell msg) {
        //TODO: implement
        System.out.println("Received TorCell " + msg);
    }

    public Promise<Channel> establishConnection(InetAddress connectAddress, int connectPort) {
        // Aggressive approach - if we can create more channels, create more
        Promise<Channel> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
        ChannelFuture connectFuture;
        connectFuture = bootstrap.connect(connectAddress, connectPort);
        connectFuture.addListener(
            (ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    channelPromise.setSuccess(channelFuture.channel());
                } else {
                    channelPromise.setFailure(channelPromise.cause() == null ?
                            new Exception("Connect Exception " + channelFuture) : channelPromise.cause());
                }
            }
        );
        return channelPromise;
    }

    public ChannelFuture handshake(Channel channel) {
        VersionsTorCell versionsCell = new VersionsTorCell(0, 3);
        return channel.write(versionsCell);
    }

    @Override
    public Future<String> loadDirectory() {
        return null;
    }

    @Override
    public Future<String> connect2() {
        return null;
    }

    @Override
    public Future<String> extend2() {
        return null;
    }

    @Override
    public void shutdown() {
        group.shutdownGracefully();
    }
}
