package com.flower.sockschain.config;

import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
public interface SocksNode {
    SocksProtocolVersion socksProtocolVersion();
    String serverAddress();
    int serverPort();

    //TLS Settings for Socks5t and Socks+
    @Nullable String clientCertificate();
    @Nullable String rootServerCertificate();

    static SocksNode of(SocksProtocolVersion socksProtocolVersion, String serverAddress, int serverPort) {
        return ImmutableSocksNode.builder()
                .socksProtocolVersion(socksProtocolVersion)
                .serverAddress(serverAddress)
                .serverPort(serverPort)
                .build();
    }
}
