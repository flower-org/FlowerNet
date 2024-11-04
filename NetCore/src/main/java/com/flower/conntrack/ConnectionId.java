package com.flower.conntrack;

import io.netty.handler.codec.socksx.SocksVersion;

public class ConnectionId {
    final long connectionNumber;
    final SocksVersion protocol;

    public ConnectionId(long connectionNumber, SocksVersion protocol) {
        this.connectionNumber = connectionNumber;
        this.protocol = protocol;
    }
}
