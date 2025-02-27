package com.flower.net.config.access;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAccessConfig.class)
@JsonDeserialize(as = ImmutableAccessConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AccessConfig {
    @JsonProperty
    Access defaultAccess();

    @JsonProperty
    @Nullable List<Rule> accessRules();
}
