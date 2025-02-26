package com.flower.net.sockschain.config.certs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableBksFileConfig.class)
@JsonDeserialize(as = ImmutableBksFileConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface BksResourceConfig {
    @JsonProperty
    String bksResourceName();

    @JsonProperty
    @Nullable
    String pin();
}
