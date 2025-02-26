package com.flower.net.config.dns;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flower.net.config.pki.Source;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableDnsServerConfig.class)
@JsonDeserialize(as = ImmutableDnsServerConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface DnsServerConfig {
    DnsType dnsType();

    @Nullable String host();
    @Nullable Integer port();
    @Nullable Source certificate();
    @Nullable String httpPath();
}
