package com.flower.sockschain.config.certs.local;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLocalCertificateFileConfig.class)
@JsonDeserialize(as = ImmutableLocalCertificateFileConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface LocalCertificateFileConfig {
    @JsonProperty
    String certificateFile();

    @JsonProperty
    String privateKeyFile();
}
