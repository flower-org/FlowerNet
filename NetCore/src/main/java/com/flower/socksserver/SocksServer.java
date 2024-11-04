package com.flower.socksserver;

import com.flower.conntrack.ConnectionListenerAndFilter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SocksServer {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

    final boolean allowDirectAccessByIpAddress;
    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    @Nullable final List<ConnectionListenerAndFilter> connectionListenerAndFilters;

    @Nullable EventLoopGroup bossGroup;
    @Nullable EventLoopGroup workerGroup;

    public SocksServer(boolean allowDirectAccessByIpAddress,
                       Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider,
                       @Nullable List<ConnectionListenerAndFilter> connectionListenerAndFilters) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
        this.connectHandlerProvider = connectHandlerProvider;
        this.connectionListenerAndFilters = connectionListenerAndFilters;
    }

    public void shutdownServer() {
        checkNotNull(bossGroup).shutdownGracefully();
        checkNotNull(workerGroup).shutdownGracefully();
    }

    public ChannelFuture startServer(int port, @Nullable SslContext sslCtx) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            //.handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new SocksServerInitializer(connectHandlerProvider, allowDirectAccessByIpAddress, sslCtx,
                    connectionListenerAndFilters));
        return b.bind(port);
    }
}
