package com.flower.socksserver;

import com.flower.conntrack.ConnectionListenerAndFilter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.ssl.SslContext;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    @Nullable private final SslContext sslCtx;
    @Nullable private final List<ConnectionListenerAndFilter> connectionListenerAndFilters;

    public SocksServerInitializer(Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider,
                                  @Nullable SslContext sslCtx,
                                  @Nullable List<ConnectionListenerAndFilter> connectionListenerAndFilters) {
        this.connectHandlerProvider = connectHandlerProvider;
        this.sslCtx = sslCtx;
        this.connectionListenerAndFilters = connectionListenerAndFilters;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        if (sslCtx != null) {
            ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
        }
        ch.pipeline().addLast(
            new SocksPortUnificationServerHandler(),
            new SocksServerHandler(connectHandlerProvider, connectionListenerAndFilters));
    }
}
