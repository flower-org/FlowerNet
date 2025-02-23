package com.flower.sockschain.config.certs.local;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flower.sockschain.config.certs.BksFileConfig;
import com.flower.sockschain.config.certs.CertificatePkcs11Config;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/** One of */
@Value.Immutable
@JsonSerialize(as = ImmutableLocalCertificate.class)
@JsonDeserialize(as = ImmutableLocalCertificate.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface LocalCertificate {
    @JsonProperty
    @Nullable CertificatePkcs11Config pkcs11Config();

    @JsonProperty
    @Nullable LocalCertificateFileConfig fileConfig();

    @JsonProperty
    @Nullable LocalCertificateResourceConfig resourceConfig();

    @JsonProperty
    @Nullable
    BksFileConfig bksConfig();
}
