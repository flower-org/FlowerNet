package com.flower.net.config.chainconf;

import java.util.List;

public interface ProxyChainProvider {
    List<SocksNode> getProxyChain();
}
