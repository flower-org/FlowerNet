package com.flower.net.sockschain.config.certs.local;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLocalCertificateResourceConfig.class)
@JsonDeserialize(as = ImmutableLocalCertificateResourceConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface LocalCertificateResourceConfig {
    @JsonProperty
    String certificateResourceName();

    @JsonProperty
    String privateKeyResourceName();
}
