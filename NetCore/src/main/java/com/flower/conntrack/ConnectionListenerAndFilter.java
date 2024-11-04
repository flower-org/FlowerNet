package com.flower.conntrack;

public interface ConnectionListenerAndFilter {
    enum AddressCheck {
        CONNECTION_ALLOWED,
        CONNECTION_PROHIBITED
    }

    AddressCheck approveConnection(String dstHost, int dstPort);
}
