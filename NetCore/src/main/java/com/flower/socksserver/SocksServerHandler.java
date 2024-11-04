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
package com.flower.socksserver;

import com.flower.utils.ServerUtil;
import com.google.common.base.Preconditions;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.flower.conntrack.ConnectionId;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.flower.conntrack.ConnectionAttributes.CONNECTION_ID_KEY;
import static com.flower.conntrack.ConnectionAttributes.getConnectionInfo;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {
    final static AtomicLong SOCKS4_COUNTER = new AtomicLong(1);
    final static AtomicLong SOCKS5_COUNTER = new AtomicLong(1);
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerHandler.class);

    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;

    public SocksServerHandler(Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider) {
        this.connectHandlerProvider = connectHandlerProvider;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) {
        switch (socksRequest.version()) {
            case SOCKS4a:
                if (!ctx.channel().hasAttr(CONNECTION_ID_KEY)) {
                    long socks4ConnectionNumber = SOCKS4_COUNTER.getAndIncrement();
                    ctx.channel().attr(CONNECTION_ID_KEY).set(new ConnectionId(socks4ConnectionNumber, socksRequest.version()));
                }

                Socks4CommandRequest socksV4CmdRequest = (Socks4CommandRequest) socksRequest;
                if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
                    ctx.pipeline().addLast(connectHandlerProvider.get());
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    LOGGER.error("DISCONNECTED {} unknown command", getConnectionInfo(ctx));
                    ctx.close();
                }
                break;
            case SOCKS5:
                if (!ctx.channel().hasAttr(CONNECTION_ID_KEY)) {
                    long socks5ConnectionNumber = SOCKS5_COUNTER.getAndIncrement();
                    ctx.channel().attr(CONNECTION_ID_KEY).set(new ConnectionId(socks5ConnectionNumber, socksRequest.version()));
                }

                if (socksRequest instanceof Socks5InitialRequest) {
                    String handlerAfterName = ServerUtil.getHandlerName(Socks5InitialRequestDecoder.class, ctx.pipeline());
                    ctx.pipeline().addBefore(handlerAfterName, "Socks5CommandRequestDecoder", new Socks5CommandRequestDecoder());
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                /*
                    // auth support example
                    //!!!! DON'T USE addFirst here it'll screw up TLS handler !!!!
                    //ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                    //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
                } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                    ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                    ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));*/
                } else if (socksRequest instanceof Socks5CommandRequest) {
                    final Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest)socksRequest;
                    if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        ctx.pipeline().addLast(connectHandlerProvider.get());
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(socksRequest);
                    } else {
                        LOGGER.error("DISCONNECTED {} unknown command", getConnectionInfo(ctx));
                        ctx.close();
                    }
                } else {
                    //TODO: close feedback
                    LOGGER.error("DISCONNECTED {} unknown request", getConnectionInfo(ctx));
                    ctx.close();
                }
                break;
            case UNKNOWN:
                //TODO: close feedback
                LOGGER.error("DISCONNECTED {} unknown protocol", getConnectionInfo(ctx));
                Preconditions.checkNotNull(ctx).close();
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //TODO: close feedback
        LOGGER.error("DISCONNECTED {} exception caught: ", getConnectionInfo(ctx), cause);
        ServerUtil.closeOnFlush(ctx.channel());
    }
}
