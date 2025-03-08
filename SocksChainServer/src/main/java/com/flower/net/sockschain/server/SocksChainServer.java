package com.flower.net.sockschain.server;

import com.flower.crypt.PkiUtil;
import com.flower.net.config.chainconf.ProxyChainProvider;
import com.flower.net.config.chainconf.SocksNode;
import com.flower.net.config.chainconf.SocksProtocolVersion;
import com.flower.net.socksserver.FlowerSslContextBuilder;
import com.flower.net.socksserver.SocksServer;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.util.List;

import static com.flower.net.trust.FlowerTrust.TRUST_MANAGER_WITH_CLIENT_CA;
import static com.google.common.base.Preconditions.checkNotNull;

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
        String bindToIp = null;
        String bindClientToIp = null;
        if (args.length > 0) {
            isSocks5OverTls = Boolean.parseBoolean(args[0]);
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        SslContext sslCtx = isSocks5OverTls ? buildSslContext() : null;

        SocksServer server = new SocksServer(() -> ALLOW_DIRECT_IP_ACCESS,
                () -> new SocksChainServerConnectHandler(HARDCODED_CHAIN_PROVIDER, () -> bindClientToIp,
                        ETokenKeyManagerProvider::getManagerOld));

        try {
            LOGGER.info("Starting on port {} TLS: {}", port, isSocks5OverTls);
            server.startServer(port, bindToIp, sslCtx)
                    .sync().channel().closeFuture().sync();
        } finally {
            server.shutdownServer();
        }
    }

    public static SslContext buildSslContext() throws SSLException {
        return FlowerSslContextBuilder.buildSslContext(
                checkNotNull(PkiUtil.getKeyManagerFromResources("socks5s_server.crt",
                "socks5s_server.key", "")), TRUST_MANAGER_WITH_CLIENT_CA);
    }
}
