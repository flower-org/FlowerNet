package com.flower.conntrack;

import java.net.SocketAddress;

public interface ConnectionListenerAndFilter {
    enum AddressCheck {
        CONNECTION_ALLOWED,
        CONNECTION_PROHIBITED
    }

    AddressCheck approveConnection(String dstHost, int dstPort, SocketAddress from);
}
