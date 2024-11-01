package com.flower.socks5s;

import com.flower.utils.PkiUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import static com.flower.trust.FlowerTrust.TRUST_MANAGER;

public class FlowerSslContextBuilder {
    public static final KeyManagerFactory SERVER_KEY_MANAGER =
            PkiUtil.getKeyManagerFromResources("socks5s_server.crt", "socks5s_server.key", "");

    public static SslContext buildSslContext() throws SSLException {
        return io.netty.handler.ssl.SslContextBuilder
                .forServer(SERVER_KEY_MANAGER)
                .trustManager(TRUST_MANAGER)
                .clientAuth(ClientAuth.REQUIRE)
                .build();
    }
}
