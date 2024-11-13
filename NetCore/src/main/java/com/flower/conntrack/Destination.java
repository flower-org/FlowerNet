package com.flower.conntrack;

public class Destination {
    public final String host;
    public final int port;

    public Destination(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host + ':' + port;
    }
}
