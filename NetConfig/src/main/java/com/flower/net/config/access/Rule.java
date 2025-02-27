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

    /** Rules, format varies based on ruleType
     * IP_ADDRESS    - ip addresses (IPv4 and IPv6)
     *                 e.g. "192.168.1.2", "2001:4860:4860::8888"
     * IP_RANGE      - ip address ranges (IPv4 and IPv6)
     *                 e.g. "192.168.0.0/16", "2001:db8::/32"
     * NAME          - names without wildcards
     *                 e.g. "google.com", "ya.ru"
     * NAME_WILDCARD - names with wildcards '*' and '?' (less performant than exact NAME match)
     *                 e.g. "server-*.company.com", "node-??.datacenter.net", "host-??-*env.cloud.org", "*error*?.sys.local"
     * PORT          - rules Must be NULL, use ports/portRanges
     */
    @JsonProperty
    @Nullable
    List<String> rules();

    @JsonProperty
    @Nullable
    List<Integer> ports();

    /** Dash-separated strings, e.g. "8080-8090" */
    @JsonProperty
    @Nullable
    List<String> portRanges();

    /** Optional rule name */
    @JsonProperty
    @Nullable
    String ruleName();
}
