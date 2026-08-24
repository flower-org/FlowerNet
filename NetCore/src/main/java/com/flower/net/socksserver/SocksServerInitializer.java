package com.flower.net.socksserver;

import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.ConnectionListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.function.Supplier;

public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerInitializer.class);

    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    private final Supplier<Boolean> allowDirectAccessByIpAddress;
    @Nullable private final SslContext sslCtx;
    @Nullable private final Collection<ConnectionFilter> connectionFilters;
    @Nullable private final Collection<ConnectionListener> connectionListeners;

    public SocksServerInitializer(Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider,
                                  Supplier<Boolean> allowDirectAccessByIpAddress,
                                  @Nullable SslContext sslCtx,
                                  @Nullable Collection<ConnectionFilter> connectionFilters,
                                  @Nullable Collection<ConnectionListener> connectionListeners) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
        this.connectHandlerProvider = connectHandlerProvider;
        this.sslCtx = sslCtx;
        this.connectionFilters = connectionFilters;
        this.connectionListeners = connectionListeners;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ch.pipeline().addLast();
        if (sslCtx != null) {
            SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
/*
            // Debug: Output TLS certificates
            sslHandler.handshakeFuture().addListener(f -> {
                if (f == null || !f.isSuccess()) {
                    LOGGER.error("mTLS handshake FAILED remote={} cause={}",
                            ch.remoteAddress(), f.cause() == null ? "unknown" : f.cause().toString(), f.cause());
                    return;
                }

                LOGGER.info("mTLS handshake OK remote={}", ch.remoteAddress());
                try {
                    SSLSession session = sslHandler.engine().getSession();
                    Certificate[] peer = session.getPeerCertificates();
                    LOGGER.info("Peer cert chain len={}", peer.length);
                    if (peer.length > 0 && peer[0] instanceof X509Certificate x509) {
                        LOGGER.info("Peer subject={} issuer={}",
                                x509.getSubjectX500Principal(), x509.getIssuerX500Principal());
                    }
                } catch (Exception e) {
                    LOGGER.error("Handshake succeeded but peer cert unavailable remote={}", ch.remoteAddress(), e);
                }
            });
            */
            ch.pipeline().addLast(sslHandler);
        }
        ch.pipeline().addLast(
            new SocksPortUnificationServerHandler(),
            new SocksServerHandler(allowDirectAccessByIpAddress, connectHandlerProvider, connectionFilters, connectionListeners));
    }
}
