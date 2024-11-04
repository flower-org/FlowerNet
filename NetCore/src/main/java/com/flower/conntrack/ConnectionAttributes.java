package com.flower.conntrack;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

public class ConnectionAttributes {
    public static final AttributeKey<ConnectionId> CONNECTION_ID_KEY =
            AttributeKey.valueOf("connection_id");
    public static final AttributeKey<Destination> DESTINATION_KEY =
            AttributeKey.valueOf("destination_key");

    public static String getConnectionInfo(ChannelHandlerContext ctx) {
        StringBuilder builder = new StringBuilder();

        Channel channel = ctx.channel();
        if (channel.hasAttr(CONNECTION_ID_KEY)) {
            ConnectionId connectionId = channel.attr(CONNECTION_ID_KEY).get();
            builder.append(String.format("[Connection# %d: %s",
                    connectionId.connectionNumber, connectionId.protocol.toString()));
        }
        if (channel.hasAttr(DESTINATION_KEY)) {
            Destination destination = channel.attr(DESTINATION_KEY).get();
            builder.append(String.format(" -> %s: %d", destination.host, destination.port));
        }

        if (builder.isEmpty()) {
            builder.append("[Unitialized connection]");
        } else {
            builder.append("]");
        }

        return builder.toString();
    }
}
