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
package com.flower.net.dns.dotclient.viasocks;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import com.flower.net.utils.ServerUtil;
import io.netty.channel.*;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks5ClientConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
    static final Logger LOGGER = LoggerFactory.getLogger(Socks5ClientConnectHandler.class);

    final DnsOverTlsClient dnsClient;
    final GenericFutureListener<Future<? super Channel>> sslHandshakeListener;

    public Socks5ClientConnectHandler(DnsOverTlsClient dnsClient,
                                      GenericFutureListener<Future<? super Channel>> sslHandshakeListener) {
        this.dnsClient = dnsClient;
        this.sslHandshakeListener = sslHandshakeListener;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) {
        if (message instanceof Socks5CommandResponse) {
            final Socks5CommandResponse response = (Socks5CommandResponse)message;
            if (response.status() == Socks5CommandStatus.SUCCESS) {
                //ALL GOOD, connection successful

//                System.out.println(showPipeline(ctx.pipeline()));

                ctx.pipeline().remove(Socks5ClientConnectHandler.this);
                ctx.pipeline().remove(Socks5CommandResponseDecoder.class);
                ctx.pipeline().remove(Socks5InitialResponseDecoder.class);
                ctx.pipeline().remove(Socks5ClientEncoder.class);

                // and then business logic.
//                ctx.pipeline().addLast(new SecureChatClientHandler());

                SslHandler sslHandler = dnsClient.getSslCtx().newHandler(ctx.channel().alloc());
                sslHandler.handshakeFuture().addListener(sslHandshakeListener);

                ctx.pipeline().addLast(sslHandler)
                .addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        super.exceptionCaught(ctx, cause);
                    }
                })
                .addLast(new TcpDnsQueryEncoder())
                .addLast(new TcpDnsResponseDecoder())
                .addLast(new SimpleChannelInboundHandler<DefaultDnsResponse>() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        LOGGER.error("DnsOverTlsClient.exceptionCaught", cause);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DefaultDnsResponse msg) {
                        try {
                            dnsClient.handleQueryResp(msg);
                        } finally {
                            ctx.close();
                        }
                    }
                });
            } else {
                ctx.close();
            }
        } else {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
