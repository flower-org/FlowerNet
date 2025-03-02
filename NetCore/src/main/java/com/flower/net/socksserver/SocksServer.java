package com.flower.net.socksserver;

import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.ConnectionListener;
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
    final Deque<ConnectionFilter> connectionFilters;
    final Deque<ConnectionListener> connectionListeners;

    @Nullable EventLoopGroup bossGroup;
    @Nullable EventLoopGroup workerGroup;

    public SocksServer(Supplier<Boolean> allowDirectAccessByIpAddress,
                       Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
        this.connectHandlerProvider = connectHandlerProvider;
        this.connectionFilters = new LinkedBlockingDeque<>();
        this.connectionListeners = new LinkedBlockingDeque<>();
    }

    public void shutdownServer() {
        checkNotNull(bossGroup).shutdownGracefully();
        checkNotNull(workerGroup).shutdownGracefully();
    }

    public void addConnectionFilter(ConnectionFilter filter) {
        connectionFilters.add(filter);
    }

    public void removeConnectionFilter(ConnectionFilter filter) {
        connectionFilters.remove(filter);
    }

    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    public ChannelFuture startServer(int port, @Nullable String boundIpAddr, @Nullable SslContext sslCtx) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            //.handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new SocksServerInitializer(connectHandlerProvider, allowDirectAccessByIpAddress, sslCtx,
                    connectionFilters, connectionListeners));
        if (boundIpAddr == null) {
            return b.bind(port);
        } else {
            return b.bind(boundIpAddr, port);
        }
    }
}
