package com.flower.net.sockschain.server;

import com.flower.net.sockschain.config.ProxyChainProvider;
import com.flower.net.sockschain.config.SocksNode;
import com.flower.net.sockschain.config.SocksProtocolVersion;
import com.flower.net.socksserver.FlowerSslContextBuilder;
import com.flower.net.socksserver.SocksServer;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class SocksChainServer {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksChainServer.class);

    final static int PORT = 1081;
    final static boolean TLS = false;
    static final boolean ALLOW_DIRECT_IP_ACCESS = false;
    static final ProxyChainProvider HARDCODED_CHAIN_PROVIDER =
        () -> List.of(
                //SocksNode.of(SocksProtocolVersion.SOCKS5s, "localhost", 8080),
                SocksNode.of(SocksProtocolVersion.SOCKS5, "10.1.1.1", 8080)//,
                //SocksNode.of(SocksProtocolVersion.SOCKS5s, "34.230.18.244", 443)*/
    );

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

        SocksServer server = new SocksServer(() -> ALLOW_DIRECT_IP_ACCESS,
                () -> new SocksChainServerConnectHandler(HARDCODED_CHAIN_PROVIDER));
        try {
            LOGGER.info("Starting on port {} TLS: {}", port, isSocks5OverTls);
            server.startServer(port, sslCtx)
                    .sync().channel().closeFuture().sync();
        } finally {
            server.shutdownServer();
        }
    }
}
