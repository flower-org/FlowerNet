package com.flower.net.config;

import java.util.List;

public interface ProxyChainProvider {
    List<SocksNode> getProxyChain();
}
