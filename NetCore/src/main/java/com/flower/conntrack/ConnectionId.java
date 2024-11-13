package com.flower.conntrack;

import io.netty.handler.codec.socksx.SocksVersion;

import java.util.Objects;

public class ConnectionId {
    final long connectionNumber;
    final SocksVersion protocol;

    public ConnectionId(long connectionNumber, SocksVersion protocol) {
        this.connectionNumber = connectionNumber;
        this.protocol = protocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionId that = (ConnectionId) o;
        return connectionNumber == that.connectionNumber && protocol == that.protocol;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionNumber, protocol);
    }

    @Override
    public String toString() {
        return connectionNumber + " / " + protocol;
    }
}
