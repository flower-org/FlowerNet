package com.flower.net.config.pki;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableSource.class)
@JsonDeserialize(as = ImmutableSource.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Source {
    @JsonProperty
    @Nullable
    String resourceName();

    @JsonProperty
    @Nullable
    String fileName();

    @JsonProperty
    @Nullable
    String pkcs11LibraryPath();

    @JsonProperty
    @Nullable
    String pkcs11pin();
}
