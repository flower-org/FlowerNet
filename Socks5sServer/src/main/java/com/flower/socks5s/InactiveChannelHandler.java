package com.flower.socks5s;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.flower.conntrack.ConnectionAttributes.getConnectionInfo;

public class InactiveChannelHandler extends ChannelInboundHandlerAdapter {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerConnectHandler.class);

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.error("DISCONNECTED {} channel inactive: ", getConnectionInfo(ctx));
        super.channelInactive(ctx);
    }
}
