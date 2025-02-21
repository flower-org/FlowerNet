package com.flower.socksserver;

import com.flower.utils.PkiUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import static com.flower.trust.FlowerTrust.TRUST_MANAGER_WITH_CLIENT_CA;

public class FlowerSslContextBuilder {
    public static final KeyManagerFactory SERVER_KEY_MANAGER =
            PkiUtil.getKeyManagerFromResources("socks5s_server.crt", "socks5s_server.key", "");

    public static SslContext buildSslContext(KeyManagerFactory serverKeyManager) throws SSLException {
        return io.netty.handler.ssl.SslContextBuilder
                .forServer(serverKeyManager)
                .trustManager(TRUST_MANAGER_WITH_CLIENT_CA)
                .clientAuth(ClientAuth.REQUIRE)
                .build();
    }

    public static SslContext buildSslContext() throws SSLException {
        return buildSslContext(SERVER_KEY_MANAGER);
    }
}
