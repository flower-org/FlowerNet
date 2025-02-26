package com.flower.net.conntrack;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import javax.annotation.Nullable;
import java.net.SocketAddress;

import static com.flower.net.conntrack.ConnectionAttributes.CONNECTION_ID_KEY;
import static com.flower.net.conntrack.ConnectionAttributes.DESTINATION_KEY;
import static com.flower.net.conntrack.ConnectionAttributes.SOURCE_KEY;

public class ConnectionInfo {
    public final Channel channel;
    @Nullable public final ConnectionId connectionId;
    @Nullable public final SocketAddress source;
    @Nullable public final Destination destination;
    public final long creationTime;

    public ConnectionInfo(Channel channel) {
        this.channel = channel;

        if (channel.hasAttr(CONNECTION_ID_KEY)) {
            connectionId = channel.attr(CONNECTION_ID_KEY).get();
        } else {
            connectionId = null;
        }
        if (channel.hasAttr(SOURCE_KEY)) {
            source = channel.attr(SOURCE_KEY).get();
        } else {
            source = null;
        }
        if (channel.hasAttr(DESTINATION_KEY)) {
            destination = channel.attr(DESTINATION_KEY).get();
        } else {
            destination = null;
        }

        this.creationTime = System.currentTimeMillis();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (connectionId != null) {
            builder.append(String.format("[Connection# %d: %s",
                    connectionId.connectionNumber, connectionId.protocol.toString()));
        }
        if (source != null) {
            builder.append(" ").append(source);
        }
        if (destination != null) {
            builder.append(String.format(" -> %s: %d", destination.host, destination.port));
        }

        if (builder.isEmpty()) {
            builder.append("[Uninitialized connection]");
        } else {
            builder.append("]");
        }

        return builder.toString();
    }

    public ChannelFuture close() {
        return channel.close();
    }
}
