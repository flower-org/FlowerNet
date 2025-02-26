package com.flower.net.config.access;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableNameRule.class)
@JsonDeserialize(as = ImmutableNameRule.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface NameRule {
    @JsonProperty
    AccessRuleType ruleType();
    @JsonProperty
    String name();
}
