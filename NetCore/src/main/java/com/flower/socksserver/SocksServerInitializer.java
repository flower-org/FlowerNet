package com.flower.socksserver;

import com.flower.conntrack.ConnectionListenerAndFilter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.ssl.SslContext;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Supplier;

public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    private final Supplier<Boolean> allowDirectAccessByIpAddress;
    @Nullable private final SslContext sslCtx;
    @Nullable private final Collection<ConnectionListenerAndFilter> connectionListenerAndFilters;

    public SocksServerInitializer(Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider,
                                  Supplier<Boolean> allowDirectAccessByIpAddress,
                                  @Nullable SslContext sslCtx,
                                  @Nullable Collection<ConnectionListenerAndFilter> connectionListenerAndFilters) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
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
            new SocksServerHandler(allowDirectAccessByIpAddress, connectHandlerProvider, connectionListenerAndFilters));
    }
}
