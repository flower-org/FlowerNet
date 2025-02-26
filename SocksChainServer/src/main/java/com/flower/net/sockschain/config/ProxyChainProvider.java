package com.flower.net.sockschain.config;

import java.util.List;

public interface ProxyChainProvider {
    List<SocksNode> getProxyChain();
}
