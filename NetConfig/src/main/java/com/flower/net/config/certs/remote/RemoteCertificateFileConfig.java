package com.flower.net.config.certs.remote;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRemoteCertificateFileConfig.class)
@JsonDeserialize(as = ImmutableRemoteCertificateFileConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface RemoteCertificateFileConfig {
    @JsonProperty
    String certificateFile();
}
