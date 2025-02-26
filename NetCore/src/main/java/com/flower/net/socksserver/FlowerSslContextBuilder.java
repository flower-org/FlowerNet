package com.flower.net.socksserver;

import com.flower.net.utils.PkiUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import java.util.List;

import static com.flower.net.trust.FlowerTrust.TRUST_MANAGER_WITH_CLIENT_CA;
import static com.google.common.base.Preconditions.checkNotNull;

public class FlowerSslContextBuilder {
    public static final List<String> TLS_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");
    public static final List<String> TLS_CIPHERS = List.of(
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

    public static SslContext buildSslContext(KeyManagerFactory serverKeyManager) throws SSLException {
        return io.netty.handler.ssl.SslContextBuilder
                .forServer(serverKeyManager)
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(TRUST_MANAGER_WITH_CLIENT_CA)
                .clientAuth(ClientAuth.REQUIRE)
                .build();
    }

    public static SslContext buildSslContext() throws SSLException {
        KeyManagerFactory embeddedServerKeyManager =
                PkiUtil.getKeyManagerFromResources("socks5s_server.crt", "socks5s_server.key", "");
        return buildSslContext(checkNotNull(embeddedServerKeyManager));
    }
}
