package com.flower.net.sockschain.config.certs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableCertificatePkcs11Config.class)
@JsonDeserialize(as = ImmutableCertificatePkcs11Config.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface CertificatePkcs11Config {
    @JsonProperty
    String libraryPath();

    @JsonProperty
    @Nullable String pin();
}
