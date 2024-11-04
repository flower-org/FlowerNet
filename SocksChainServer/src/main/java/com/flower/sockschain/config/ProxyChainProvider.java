package com.flower.sockschain.config;

import java.util.List;

public interface ProxyChainProvider {
    List<SocksNode> getProxyChain();
}
