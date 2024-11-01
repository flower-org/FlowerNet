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
package com.flower.sockschain.client;

import com.flower.utils.ServerUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

import javax.net.ssl.SSLException;

@ChannelHandler.Sharable
public final class SocksChainClientConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private final SocksChainClient socksChainClient;

    public SocksChainClientConnectHandler(SocksChainClient socksChainClient) {
        this.socksChainClient = socksChainClient;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws SSLException {
        if (message instanceof Socks5CommandResponse) {
            final Socks5CommandResponse response = (Socks5CommandResponse)message;
            if (response.status() == Socks5CommandStatus.SUCCESS) {
                //ALL GOOD, connection successful
                socksChainClient.connectNextNode();

                /*System.out.println(showPipeline(ctx.pipeline()));
                ChannelFuture responseFuture =
                        inboundChannel.writeAndFlush(commandSuccess);

                responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                    ctx.pipeline().remove(SocksChainClientConnectHandler.this);//This can't stay, should be removed.
                    ctx.pipeline().remove(Socks5CommandResponseDecoder.class);//This is useless, SUCCESS state just wastes cycles.
                    ctx.pipeline().remove(Socks5InitialResponseDecoder.class);//This is useless, SUCCESS state just wastes cycles.
                    ctx.pipeline().remove(Socks5ClientEncoder.class);//This doesn't make any sense. Protocol is done, no more messages to follow.

                    inboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                    ctx.pipeline().addLast(new RelayHandler(inboundChannel));
                });

                System.out.println(showPipeline(ctx.pipeline()));
                */
            } else {
                socksChainClient.connectionFailed();
            }
        } else {
            socksChainClient.connectionFailed();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
