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
package com.flower.net.sockschain.server;

import com.flower.net.utils.ServerUtil;
import com.flower.net.sockschain.client.SocksChainClient;
import com.flower.net.config.ProxyChainProvider;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;

import javax.net.ssl.SSLException;

@ChannelHandler.Sharable
public final class SocksChainServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
    final ProxyChainProvider proxyChainProvider;

    public SocksChainServerConnectHandler(ProxyChainProvider proxyChainProvider) {
        this.proxyChainProvider = proxyChainProvider;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws SSLException {
        ctx.pipeline().remove(SocksChainServerConnectHandler.this);
        // TODO: is it excessive to create a new client every time?
        new SocksChainClient(ctx, message, proxyChainProvider.getProxyChain()).connectChain();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
