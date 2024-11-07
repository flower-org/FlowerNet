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
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SocksServer {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

    final Supplier<Boolean> allowDirectAccessByIpAddress;
    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    final Deque<ConnectionListenerAndFilter> connectionListenerAndFilters;

    @Nullable EventLoopGroup bossGroup;
    @Nullable EventLoopGroup workerGroup;

    public SocksServer(Supplier<Boolean> allowDirectAccessByIpAddress,
                       Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
        this.connectHandlerProvider = connectHandlerProvider;
        this.connectionListenerAndFilters = new LinkedBlockingDeque<>();
    }

    public void shutdownServer() {
        checkNotNull(bossGroup).shutdownGracefully();
        checkNotNull(workerGroup).shutdownGracefully();
    }

    public void addConnectionListenerAndFilter(ConnectionListenerAndFilter filter) {
        connectionListenerAndFilters.add(filter);
    }

    public void removeConnectionListenerAndFilter(ConnectionListenerAndFilter filter) {
        connectionListenerAndFilters.remove(filter);
    }

    public ChannelFuture startServer(int port, @Nullable SslContext sslCtx) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            //.handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new SocksServerInitializer(connectHandlerProvider, allowDirectAccessByIpAddress, sslCtx,
                    connectionListenerAndFilters));
        return b.bind(port);
    }
}
