package com.flower.net.socksserver;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import java.util.List;

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
                .clientAuth(ClientAuth.REQUIRE)
                .build();
    }

    public static SslContext buildSslContext(KeyManagerFactory serverKeyManager,
                                             TrustManagerFactory trustManagerFactory) throws SSLException {
        return io.netty.handler.ssl.SslContextBuilder
                .forServer(serverKeyManager)
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(trustManagerFactory)
                .clientAuth(ClientAuth.REQUIRE)
                .build();
    }
}
