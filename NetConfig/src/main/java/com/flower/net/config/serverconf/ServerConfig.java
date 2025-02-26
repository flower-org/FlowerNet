package com.flower.net.config.serverconf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flower.net.config.access.AccessConfig;
import com.flower.net.config.dns.DnsServerConfig;
import com.flower.net.config.dns.DnsType;
import com.flower.net.config.pki.CertificateConfig;
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
    Boolean directIpAccess();

    @JsonProperty
    @Nullable
    AccessConfig accessConfig();

    @JsonProperty
    @Nullable
    Boolean selfGeneratedCertificate();

    @JsonProperty
    @Nullable
    CertificateConfig certificate();

    @JsonProperty
    @Nullable
    DnsServerConfig defaultNameResolution();

    @JsonProperty
    @Nullable
    List<DnsType> allowedNameResolutionType();
}
