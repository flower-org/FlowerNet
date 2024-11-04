package com.flower.socks5s;

import com.flower.socksserver.FlowerSslContextBuilder;
import com.flower.socksserver.SocksServer;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Socks5sServer {
    final static Logger LOGGER = LoggerFactory.getLogger(Socks5sServer.class);

    static final boolean DEFAULT_IS_SOCKS5_OVER_TLS = false;
    static final int DEFAULT_PORT = 8080;
    static final boolean ALLOW_DIRECT_IP_ACCESS = true;

    public static void main(String[] args) throws Exception {
        boolean isSocks5OverTls = DEFAULT_IS_SOCKS5_OVER_TLS;
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            isSocks5OverTls = Boolean.parseBoolean(args[0]);
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        SslContext sslCtx = isSocks5OverTls ? FlowerSslContextBuilder.buildSslContext() : null;

        SocksServer server = new SocksServer(ALLOW_DIRECT_IP_ACCESS, SocksServerConnectHandler::new, null);
        try {
            LOGGER.info("Starting on port {} TLS: {}", port, isSocks5OverTls);
            server.startServer(port, sslCtx)
                    .sync().channel().closeFuture().sync();
        } finally {
            server.shutdownServer();
        }
    }
}
