package com.flower.socksserver;

import com.flower.utils.PkiUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import java.util.List;

import static com.flower.trust.FlowerTrust.TRUST_MANAGER_WITH_CLIENT_CA;

public class FlowerSslContextBuilder {
    final static Logger LOGGER = LoggerFactory.getLogger(FlowerSslContextBuilder.class);

    public static final List<String> TLS_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");
    public static final List<String> TLS_CIPHERS = List.of(
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

    @Nullable public static KeyManagerFactory SERVER_KEY_MANAGER;
    static {
        try {
            SERVER_KEY_MANAGER = PkiUtil.getKeyManagerFromResources("socks5s_server.crt", "socks5s_server.key", "");
        } catch (Exception e) {
            LOGGER.info("SERVER_KEY_MANAGER init error: ", e);
            SERVER_KEY_MANAGER = null;
        }
    }

    public static SslContext buildSslContext() throws SSLException {
        return io.netty.handler.ssl.SslContextBuilder
                .forServer(SERVER_KEY_MANAGER)
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(TRUST_MANAGER_WITH_CLIENT_CA)
                .clientAuth(ClientAuth.REQUIRE)
                .build();
    }
}
