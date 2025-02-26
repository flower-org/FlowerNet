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
package com.flower.net.socks5s;

import com.flower.net.access.AccessManager;
import com.flower.net.handlers.RelayHandler;
import com.flower.net.utils.ImmediateFuture;
import com.flower.net.utils.IpAddressUtil;
import com.flower.net.utils.ServerUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
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
import io.netty.util.NetUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import static com.flower.net.conntrack.ConnectionAttributes.getConnectionInfo;
import static com.flower.net.utils.ServerUtil.showPipeline;
import com.flower.net.dns.DnsClient;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerConnectHandler.class);

    private final Bootstrap b = new Bootstrap();
    private final AccessManager accessManager;
    private final DnsClient dnsClient;

    public SocksServerConnectHandler(AccessManager accessManager, DnsClient dnsClient) {
        this.accessManager = accessManager;
        this.dnsClient = dnsClient;
    }

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

            String hostname = request.dstAddr();
            if (hostnameProhibited(hostname)) {
                LOGGER.error("DISCONNECTED {} hostname prohibited. " + hostname, getConnectionInfo(ctx));
                ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                ServerUtil.closeOnFlush(ctx.channel());
            } else {
                resolve(hostname, ctx.executor()).addListener(
                    resolveFuture -> {
                        if (resolveFuture.isSuccess()) {
                            InetAddress addr = (InetAddress) resolveFuture.getNow();
                            if (ipAddressProhibited(addr)) {
                                LOGGER.error("DISCONNECTED {} IpAddress prohibited. " + addr, getConnectionInfo(ctx));
                                ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                                ServerUtil.closeOnFlush(ctx.channel());
                            } else {
                                b.connect(addr, request.dstPort()).addListener((ChannelFutureListener) future -> {
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
                            }
                        } else {
                            // Close the connection if the connection attempt has failed.
                            ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                            LOGGER.error("DISCONNECTED {} name resolution failed {}", getConnectionInfo(ctx), hostname);
                            ServerUtil.closeOnFlush(ctx.channel());
                        }
                    }
                );
            }
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

                                    LOGGER.info("CONNECTED {} {}", getConnectionInfo(ctx), outboundChannel.remoteAddress());
                                });
                            }
                        } else {
                            LOGGER.error("DISCONNECTED {} connection failed", getConnectionInfo(ctx), future.cause());
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

            String hostname = request.dstAddr();
            if (hostnameProhibited(hostname)) {
                LOGGER.error("DISCONNECTED {} hostname prohibited. " + hostname, getConnectionInfo(ctx));
                ctx.channel().writeAndFlush(
                        new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, request.dstAddrType()));
                ServerUtil.closeOnFlush(ctx.channel());
            } else {
                resolve(hostname, ctx.executor()).addListener(
                    resolveFuture -> {
                        if (resolveFuture.isSuccess()) {
                            InetAddress addr = (InetAddress) resolveFuture.getNow();
                            if (ipAddressProhibited(addr)) {
                                LOGGER.error("DISCONNECTED {} IpAddress prohibited. " + addr, getConnectionInfo(ctx));
                                ctx.channel().writeAndFlush(
                                        new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, request.dstAddrType()));
                                ServerUtil.closeOnFlush(ctx.channel());
                            } else {
                                b.connect(addr, request.dstPort()).addListener((ChannelFutureListener) future -> {
                                    if (future.isSuccess()) {
                                        // Connection established use handler provided results
                                    } else {
                                        LOGGER.error("DISCONNECTED {} connection failed", getConnectionInfo(ctx), future.cause());
                                        // Close the connection if the connection attempt has failed.
                                        ctx.channel().writeAndFlush(
                                                new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
                                        ServerUtil.closeOnFlush(ctx.channel());
                                    }
                                });
                            }
                        } else {
                            LOGGER.error("DISCONNECTED {} name resolution failed {}", getConnectionInfo(ctx), hostname, resolveFuture.cause());
                            // Close the connection if the connection attempt has failed.
                            ctx.channel().writeAndFlush(
                                    new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
                            ServerUtil.closeOnFlush(ctx.channel());
                        }
                    }
                );
            }
        } else {
            LOGGER.error("DISCONNECTED {} unknown protocol " + message.getClass(), getConnectionInfo(ctx));
            ctx.close();
        }
    }

    Future<InetAddress> resolve(String address, EventExecutor executor) {
        if (IpAddressUtil.isIPAddress(address)) {
            return ImmediateFuture.of(IpAddressUtil.fromString(address));
        } else {
            Promise<InetAddress> transformedPromise = new DefaultPromise<>(executor);
            Future<DnsResponse> originalFuture = dnsClient.query(address, 2500);
            originalFuture.addListener((Future<DnsResponse> f) -> {
                if (f.isSuccess()) {
                    DnsResponse msg = f.getNow();

                    List<InetAddress> addresses = new ArrayList<>();
                    for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
                        DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
                        if (DnsRecordType.A.equals(record.type())) {
                            //just print the IP after query
                            DnsRawRecord raw = (DnsRawRecord) record;
                            addresses.add(IpAddressUtil.fromString(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content()))));
                        } else if (DnsRecordType.AAAA.equals(record.type())) {
                            //just print the IP after query
                            DnsRawRecord raw = (DnsRawRecord) record;
                            addresses.add(IpAddressUtil.fromString(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content()))));
                        }
                    }
                    if (!addresses.isEmpty()) {
                        transformedPromise.setSuccess(addresses.get(ThreadLocalRandom.current().nextInt(addresses.size())));
                    } else {
                        transformedPromise.setFailure(f.cause());
                    }
                } else {
                    transformedPromise.setFailure(f.cause());
                }
            });

            return transformedPromise;
        }
    }

    public boolean hostnameProhibited(String name) {
        return !accessManager.isAllowed(name);
    }

    public boolean ipAddressProhibited(InetAddress addr) {
        return !accessManager.isAllowed(addr);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("DISCONNECTED {} exception caught: ", getConnectionInfo(ctx), cause);
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
