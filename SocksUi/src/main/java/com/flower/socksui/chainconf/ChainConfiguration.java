package com.flower.socksui.chainconf;

import com.flower.sockschain.config.SocksNode;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface ChainConfiguration {
    List<SocksNode> knownProxyServers();
    List<SocksNode> proxyChain();
}
