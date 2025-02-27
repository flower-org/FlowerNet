package com.flower.net.config.serverconf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flower.net.config.access.AccessConfig;
import com.flower.net.config.dns.DnsServerConfig;
import com.flower.net.config.dns.DnsType;
import com.flower.net.config.pki.CertificateConfig;
import com.flower.net.config.pki.Source;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableServerConfig.class)
@JsonDeserialize(as = ImmutableServerConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface ServerConfig {
    @JsonProperty
    @Nullable
    Integer port();

    @JsonProperty
    @Nullable
    Boolean tls();

    @JsonProperty
    @Nullable
    AccessConfig accessConfig();

    @JsonProperty
    @Nullable
    CertificateConfig certificate();

    @JsonProperty
    @Nullable
    Source clientCertificate();

    @JsonProperty
    @Nullable
    DnsServerConfig dns();

    @JsonProperty
    @Nullable
    List<DnsType> userDns();
}
