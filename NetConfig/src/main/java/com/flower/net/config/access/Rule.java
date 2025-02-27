package com.flower.net.config.access;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableRule.class)
@JsonDeserialize(as = ImmutableRule.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Rule {
    @JsonProperty
    RuleType ruleType();
    @JsonProperty
    Access access();
    @JsonProperty
    @Nullable
    List<String> rules();
    @JsonProperty
    @Nullable
    List<Integer> ports();
    @JsonProperty
    @Nullable
    List<PortRange> portRanges();

    @JsonProperty
    @Nullable
    String ruleName();

    @Value.Immutable
    @JsonSerialize(as = ImmutablePortRange.class)
    @JsonDeserialize(as = ImmutablePortRange.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    interface PortRange {
        @JsonProperty
        Integer from();
        @JsonProperty
        Integer to();
    }
}
