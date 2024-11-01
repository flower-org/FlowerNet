package com.flower.sockschain.config;

import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
public interface SocksNode {
    SocksProtocolVersion socksProtocolVersion();
    AddressType serverAddressType();
    String serverAddress();
    int serverPort();

    //TLS Settings for Socks5t and Socks+
    @Nullable String clientCertificate();
    @Nullable String rootServerCertificate();

    static SocksNode of(SocksProtocolVersion socksProtocolVersion, AddressType serverAddressType, String serverAddress, int serverPort) {
        return ImmutableSocksNode.builder()
                .socksProtocolVersion(socksProtocolVersion)
                .serverAddressType(serverAddressType)
                .serverAddress(serverAddress)
                .serverPort(serverPort)
                .build();
    }
}
