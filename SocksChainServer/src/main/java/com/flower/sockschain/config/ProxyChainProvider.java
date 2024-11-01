package com.flower.sockschain.config;

import java.util.List;

public class ProxyChainProvider {
    public static List<SocksNode> getProxyChain() {
        return List.of(
                SocksNode.of(SocksProtocolVersion.SOCKS5s, AddressType.DOMAIN, "localhost", 8080),
                SocksNode.of(SocksProtocolVersion.SOCKS5, AddressType.DOMAIN, "10.1.1.1", 8080)
        );
    }
}
