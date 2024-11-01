/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.flower.socks5s;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SocksServer {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

    static boolean IS_SOCKS5_OVER_TLS = false;
    static int PORT = 8080;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            IS_SOCKS5_OVER_TLS = Boolean.parseBoolean(args[0]);
        }
        if (args.length > 0) {
            PORT = Integer.parseInt(args[1]);
        }

        SslContext sslCtx = IS_SOCKS5_OVER_TLS ? FlowerSslContextBuilder.buildSslContext() : null;

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            LOGGER.info("Starting on port {}", PORT);
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
//             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new SocksServerInitializer(sslCtx));
            b.bind(PORT).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
