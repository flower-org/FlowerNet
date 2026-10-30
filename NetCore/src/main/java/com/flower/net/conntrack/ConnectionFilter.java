package com.flower.net.conntrack;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;

import javax.annotation.Nullable;
import java.net.SocketAddress;

public interface ConnectionFilter {
        @Nullable Access approveConnection(String dstHost, int dstPort, Protocol protocol, @Nullable SocketAddress from);

        /**
         * Whether the endpoint is a server according to this filter's rules (a host+port rule matches it).
         * Lets packet-level callers, which can't tell who initiated a connection, pick the server side as destination.
         */
        default boolean isKnownServer(String host, int port) {
            return false;
        }

        void allow(String host, int port);
        void deny(String host, int port);
        void allowHost(String host);
        void denyHost(String host);
        void allowPort(int port);
        void denyPort(int port);
}
