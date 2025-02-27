package com.flower.net.conntrack;

import com.flower.net.access.Access;

import java.net.SocketAddress;

public interface ConnectionFilter {
        Access approveConnection(String dstHost, int dstPort, SocketAddress from);
}
