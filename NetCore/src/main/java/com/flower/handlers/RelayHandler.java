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
package com.flower.handlers;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.flower.utils.ServerUtil;

public final class RelayHandler extends ChannelInboundHandlerAdapter {
    final static Logger LOGGER = LoggerFactory.getLogger(RelayHandler.class);

    private final Channel relayChannel;

    public RelayHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

    //TODO: channels lifecycle can be tracked here, when they're getting active and getting inactive (for monitoring UI)
    //TODO: however, channel initialization better be tracked when the connection is created, and channel info to be filled upon reception of Socks CONNECT command.

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            //LOGGER.info("Relayed {}", msg);
            relayChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (relayChannel.isActive()) {
            ServerUtil.closeOnFlush(relayChannel);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("RelayHandler exception caught: ", cause);
        ctx.close();
        if (relayChannel.isActive()) {
            ServerUtil.closeOnFlush(relayChannel);
        }
    }
}
