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
package com.flower.dns.dotclient.viasocks;

import com.flower.dns.dotclient.DnsOverTlsClient;
import com.flower.utils.ServerUtil;
import com.google.common.base.Preconditions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Handles a client-side channel.
 */
@ChannelHandler.Sharable
public final class Socks5ClientHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private final String dstAddr;
    private final int dstPort;
    final DnsOverTlsClient dnsClient;
    final GenericFutureListener<Future<? super Channel>> sslHandshakeListener;

    public Socks5ClientHandler(String dstAddr, int dstPort, DnsOverTlsClient dnsClient,
                               GenericFutureListener<Future<? super Channel>> sslHandshakeListener) {
        this.dstAddr = dstAddr;
        this.dstPort = dstPort;
        this.dnsClient = dnsClient;
        this.sslHandshakeListener = sslHandshakeListener;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksResponse) {
        switch (socksResponse.version()) {
            case SOCKS5:
                if (socksResponse instanceof Socks5InitialResponse) {
                    String handlerAfterName = ServerUtil.getHandlerName(Socks5InitialResponseDecoder.class, ctx.pipeline());
                    ctx.pipeline().addBefore(handlerAfterName, "Socks5CommandResponseDecoder", new Socks5CommandResponseDecoder());
                    ctx.write(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN, dstAddr, dstPort));
                } else if (socksResponse instanceof Socks5CommandResponse) {
                    final Socks5CommandResponse socks5CmdResponse = (Socks5CommandResponse)socksResponse;
                    if (socks5CmdResponse.status() == Socks5CommandStatus.SUCCESS) {
                        ctx.pipeline().addLast(new Socks5ClientConnectHandler(dnsClient, sslHandshakeListener));
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(socksResponse);
                    } else {
                        ctx.close();
                    }
                } else {
                    ctx.close();
                }
                break;
            case UNKNOWN:
                Preconditions.checkNotNull(ctx).close();
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        throwable.printStackTrace();
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
