package com.flower.sockschain.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableSocksNode.class)
@JsonDeserialize(as = ImmutableSocksNode.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface SocksNode {
    @JsonProperty
    SocksProtocolVersion socksProtocolVersion();

    @JsonProperty
    String serverAddress();

    @JsonProperty
    int serverPort();

    //TLS Settings for Socks5t and Socks+
    @JsonProperty
    @Nullable String clientCertificate();

    @JsonProperty
    @Nullable String rootServerCertificate();

    static SocksNode of(SocksProtocolVersion socksProtocolVersion, String serverAddress, int serverPort) {
        return ImmutableSocksNode.builder()
                .socksProtocolVersion(socksProtocolVersion)
                .serverAddress(serverAddress)
                .serverPort(serverPort)
                .build();
    }
}
