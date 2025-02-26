package com.flower.net.conntrack;

import java.net.SocketAddress;

public interface ConnectionFilter {
        AddressCheck approveConnection(String dstHost, int dstPort, SocketAddress from);
}
