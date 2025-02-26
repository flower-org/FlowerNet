package com.flower.net.config.certs.remote;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRemoteCertificateResourceConfig.class)
@JsonDeserialize(as = ImmutableRemoteCertificateResourceConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface RemoteCertificateResourceConfig {
    @JsonProperty
    String certificateResourceName();
}
