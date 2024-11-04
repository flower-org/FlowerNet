package com.flower.socksserver;

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
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SocksServer {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;

    @Nullable EventLoopGroup bossGroup;
    @Nullable EventLoopGroup workerGroup;

    public SocksServer(Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider) {
        this.connectHandlerProvider = connectHandlerProvider;
    }

    public void shutdownServer() {
        checkNotNull(bossGroup).shutdownGracefully();
        checkNotNull(workerGroup).shutdownGracefully();
    }

    public ChannelFuture startServer(boolean isSocks5OverTls, int port, @Nullable SslContext sslCtx) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            //.handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new SocksServerInitializer(connectHandlerProvider, sslCtx));
        return b.bind(port);
    }
}
