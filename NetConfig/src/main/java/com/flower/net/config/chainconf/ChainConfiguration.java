package com.flower.net.config.chainconf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flower.net.config.SocksNode;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableChainConfiguration.class)
@JsonDeserialize(as = ImmutableChainConfiguration.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface ChainConfiguration {
    @JsonProperty
    List<SocksNode> knownProxyServers();

    @JsonProperty
    List<SocksNode> proxyChain();
}
