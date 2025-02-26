package com.flower.net.conntrack;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

public class ConnectionAttributes {
    final static Logger LOGGER = LoggerFactory.getLogger(ConnectionAttributes.class);

    public static final AttributeKey<ConnectionId> CONNECTION_ID_KEY =
            AttributeKey.valueOf("connection_id");
    public static final AttributeKey<ConnectionListener> CONNECTION_LISTENER_KEY =
            AttributeKey.valueOf("connection_listener");
    public static final AttributeKey<SocketAddress> SOURCE_KEY =
            AttributeKey.valueOf("source_key");
    public static final AttributeKey<Destination> DESTINATION_KEY =
            AttributeKey.valueOf("destination_key");

    public static ConnectionInfo getConnectionInfo(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        return new ConnectionInfo(channel);
    }

    public static ConnectionInfo reportDisconnect(ChannelHandlerContext ctx, String reason) {
        Channel channel = ctx.channel();
        return reportDisconnect(channel, reason);
    }

    public static ConnectionInfo reportDisconnect(Channel channel, String reason) {
        LOGGER.error("Report Disconnect");
        if (channel.hasAttr(CONNECTION_LISTENER_KEY)) {
            if (channel.hasAttr(CONNECTION_ID_KEY)) {
                LOGGER.error("Report Disconnect " + channel.attr(CONNECTION_ID_KEY).get());
                channel.attr(CONNECTION_LISTENER_KEY).get().disconnecting(channel.attr(CONNECTION_ID_KEY).get(), reason);
            }
        }
        return new ConnectionInfo(channel);
    }
}
