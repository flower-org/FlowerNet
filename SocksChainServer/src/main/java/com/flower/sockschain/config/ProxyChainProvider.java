package com.flower.sockschain.config;

import java.util.List;

public class ProxyChainProvider {
    public static List<SocksNode> getProxyChain() {
        return List.of(
                SocksNode.of(SocksProtocolVersion.SOCKS5, AddressType.DOMAIN, "localhost", 1080),
                SocksNode.of(SocksProtocolVersion.SOCKS5, AddressType.DOMAIN, "localhost", 1082),
                SocksNode.of(SocksProtocolVersion.SOCKS5, AddressType.DOMAIN, "localhost", 1083)
        );
    }
}
