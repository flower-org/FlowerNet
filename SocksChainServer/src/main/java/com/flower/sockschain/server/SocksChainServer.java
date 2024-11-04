package com.flower.sockschain.server;

import com.flower.socksserver.FlowerSslContextBuilder;
import com.flower.socksserver.SocksServer;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SocksChainServer {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksChainServer.class);

    final static int PORT = 1081;
    final static boolean TLS = false;
    static final boolean ALLOW_DIRECT_IP_ACCESS = false;

    public static void main(String[] args) throws Exception {
        boolean isSocks5OverTls = TLS;
        int port = PORT;
        if (args.length > 0) {
            isSocks5OverTls = Boolean.parseBoolean(args[0]);
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        SslContext sslCtx = isSocks5OverTls ? FlowerSslContextBuilder.buildSslContext() : null;

        SocksServer server = new SocksServer(ALLOW_DIRECT_IP_ACCESS, SocksChainServerConnectHandler::new, null);
        try {
            LOGGER.info("Starting on port {} TLS: {}", port, isSocks5OverTls);
            server.startServer(port, sslCtx)
                    .sync().channel().closeFuture().sync();
        } finally {
            server.shutdownServer();
        }
    }
}
