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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

import static com.flower.socks5s.ConnectionAttributes.DESTINATION_KEY;
import static com.flower.socks5s.ConnectionAttributes.getConnectionInfo;
import static com.flower.socks5s.ServerUtil.showPipeline;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerConnectHandler.class);

    private final Bootstrap b = new Bootstrap();

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) {
        if (message instanceof Socks4CommandRequest) {
            final Socks4CommandRequest request = (Socks4CommandRequest)message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    (FutureListener<Channel>) future -> {
                        final Channel outboundChannel = future.getNow();
                        if (future.isSuccess()) {
                            ChannelFuture responseFuture = ctx.channel().writeAndFlush(
                                    new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));

                            responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                                ctx.pipeline().remove(SocksServerConnectHandler.this);
                                outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                ctx.pipeline().addLast(new RelayHandler(outboundChannel));

                                LOGGER.info("CONNECTED {}", getConnectionInfo(ctx));
                            });
                        } else {
                            LOGGER.error("DISCONNECTED {} connection failed", getConnectionInfo(ctx));
                            ctx.channel().writeAndFlush(
                                    new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                            ServerUtil.closeOnFlush(ctx.channel());
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            ctx.channel().attr(DESTINATION_KEY).set(new Destination(request.dstAddr(), request.dstPort()));

            b.connect(request.dstAddr(), request.dstPort()).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Connection established use handler provided results
                } else {
                    // Close the connection if the connection attempt has failed.
                    ctx.channel().writeAndFlush(
                            new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                    );
                    LOGGER.error("DISCONNECTED {} connection failed", getConnectionInfo(ctx));
                    ServerUtil.closeOnFlush(ctx.channel());
                }
            });
        } else if (message instanceof Socks5CommandRequest) {
            final Socks5CommandRequest request = (Socks5CommandRequest)message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    (FutureListener<Channel>) future -> {
                        final Channel outboundChannel = future.getNow();
                        if (future.isSuccess()) {
                            if (!ctx.channel().isOpen()) {
                                LOGGER.error("DISCONNECTED {} incoming connection closed", getConnectionInfo(ctx));
                                ServerUtil.closeOnFlush(outboundChannel);
                            } else {
                                ChannelFuture responseFuture =
                                        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                                Socks5CommandStatus.SUCCESS,
                                                request.dstAddrType(),
                                                request.dstAddr(),
                                                request.dstPort()));

                                responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                                    String element = "SocksServerConnectHandler";
                                    String pipeline = showPipeline(ctx.pipeline());
                                    try {
                                        ctx.pipeline().remove(SocksServerConnectHandler.this);
                                        element = "Socks5CommandRequestDecoder";
                                        ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
                                        element = "Socks5InitialRequestDecoder";
                                        ctx.pipeline().remove(Socks5InitialRequestDecoder.class);
                                        element = "Socks5ServerEncoder";
                                        ctx.pipeline().remove(Socks5ServerEncoder.class);
                                    } catch (NoSuchElementException nse) {
                                        LOGGER.error("SOCKS5 {} No such element: {} pipeline {}", getConnectionInfo(ctx), element, pipeline);
                                        throw nse;
                                    }

                                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                    ctx.pipeline().addLast(new InactiveChannelHandler());
                                    ctx.pipeline().addLast(new RelayHandler(outboundChannel));

                                    LOGGER.info("CONNECTED {}", getConnectionInfo(ctx));

//                                System.out.println(showPipeline(ctx.pipeline()));
//                                System.out.println(showPipeline(outboundChannel.pipeline()));
                                });
                            }
                        } else {
                            LOGGER.error("DISCONNECTED {} connection failed", getConnectionInfo(ctx));
                            ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                    Socks5CommandStatus.FAILURE, request.dstAddrType()));
                            ServerUtil.closeOnFlush(ctx.channel());
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            ctx.channel().attr(DESTINATION_KEY).set(new Destination(request.dstAddr(), request.dstPort()));

            b.connect(request.dstAddr(), request.dstPort()).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Connection established use handler provided results
                } else {
                    LOGGER.error("DISCONNECTED {} connection failed", getConnectionInfo(ctx));
                    // Close the connection if the connection attempt has failed.
                    ctx.channel().writeAndFlush(
                            new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
                    ServerUtil.closeOnFlush(ctx.channel());
                }
            });
        } else {
            LOGGER.error("DISCONNECTED {} unknown protocol", getConnectionInfo(ctx));
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("DISCONNECTED {} exception caught: ", getConnectionInfo(ctx), cause);
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
