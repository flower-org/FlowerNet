package com.flower.net.config.pki;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableCertificateConfig.class)
@JsonDeserialize(as = ImmutableCertificateConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface CertificateConfig {
    @JsonProperty
    Source certificate();

    @JsonProperty
    @Nullable
    Source privateKey();
}
