package com.flower.sockschain.client;

import com.google.common.base.Preconditions;
import com.flower.utils.ServerUtil;
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

public class SocksChainClientHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private final SocksChainClient client;
    private final String address;
    private final int port;

    public SocksChainClientHandler(String address, int port, SocksChainClient client) {
        this.client = client;
        this.address = address;
        this.port = port;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksResponse) {
        switch (socksResponse.version()) {
            //TODO: case SOCKS4: - support other SOCKS protocols
            case SOCKS5:
                //TODO: support SOCKS5 password
                if (socksResponse instanceof Socks5InitialResponse) {
                    String handlerAfterName = ServerUtil.getHandlerName(Socks5InitialResponseDecoder.class, ctx.pipeline());
                    ctx.pipeline().addBefore(handlerAfterName, "Socks5CommandResponseDecoder", new Socks5CommandResponseDecoder());

//                    ctx.pipeline().addFirst(new Socks5CommandResponseDecoder());
                    ctx.write(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN, address, port));
                } else if (socksResponse instanceof Socks5CommandResponse) {
                    final Socks5CommandResponse socks5CmdResponse = (Socks5CommandResponse)socksResponse;
                    if (socks5CmdResponse.status() == Socks5CommandStatus.SUCCESS) {
                        ctx.pipeline().addLast(new SocksChainClientConnectHandler(client));
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
