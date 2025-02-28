package com.flower.net.socks5s;

import com.flower.crypt.PkiUtil;
import com.flower.net.config.access.Access;
import com.flower.net.config.access.AccessConfig;
import com.flower.net.config.access.AccessManager;
import com.flower.net.access.ConfigAccessManager;
import com.flower.net.config.dns.DnsServerConfig;
import com.flower.net.config.dns.DnsType;
import com.flower.net.config.exception.ConfigurationException;
import com.flower.net.config.serverconf.ServerConfig;
import com.flower.net.dns.DnsClient;
import com.flower.net.dns.cache.DnsCache;
import com.flower.net.dns.client.dnsoverhttps1.DnsOverHttps1Client;
import com.flower.net.dns.client.dnsoverhttps2.DnsOverHttps2Client;
import com.flower.net.dns.client.dnsovertls.DnsOverTlsClient;
import com.flower.net.dns.client.dnsoverudp.DnsOverUdpClient;
import com.flower.net.dns.client.os.RawOsResolver;
import com.flower.net.socksserver.SocksServer;
import com.flower.net.utils.IpAddressUtil;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileNotFoundException;
import java.net.InetAddress;

public final class Socks5sServer {
    final static Logger LOGGER = LoggerFactory.getLogger(Socks5sServer.class);

    static final boolean DEFAULT_IS_SOCKS5_OVER_TLS = true;
    static final int DEFAULT_PORT = 8443;
    static final boolean DEFAULT_ALLOW_DIRECT_IP_ACCESS = true;

    public static void run(ServerConfig serverConfig) throws Exception {
        int port = serverConfig.port() == null ? DEFAULT_PORT : serverConfig.port();
        boolean isSocks5OverTls = serverConfig.tls() == null ? DEFAULT_IS_SOCKS5_OVER_TLS : serverConfig.tls();
        boolean allowDirectIpAccess =
                serverConfig.accessConfig() == null || serverConfig.accessConfig().directIpAccess() == null
                        ? DEFAULT_ALLOW_DIRECT_IP_ACCESS
                        : serverConfig.accessConfig().directIpAccess();

        AccessManager accessManager = buildAccessManager(serverConfig.accessConfig());
        DnsClient dnsClient = buildDnsClient(serverConfig.dns());
        SslContext sslCtx = ConfigSslContextBuilder.buildSslContext(serverConfig);

        SocksServer server = new SocksServer(() -> allowDirectIpAccess,
                () -> new SocksServerConnectHandler(accessManager, dnsClient));
        try {
            LOGGER.info("Starting on port {} TLS: {}", port, isSocks5OverTls);
            server.startServer(port, sslCtx)
                    .sync().channel().closeFuture().sync();
        } finally {
            server.shutdownServer();
        }
    }

    static AccessManager allowAll() {
        return new AccessManager() {
            @Override public Access accessCheck(InetAddress address, int port) { return Access.ALLOW; }
            @Override public Access accessCheck(String name, int port) { return Access.ALLOW; }
        };
    }

    static AccessManager buildAccessManager(@Nullable AccessConfig accessConfig) {
        return accessConfig == null ? allowAll() : new ConfigAccessManager(accessConfig);
    }

    static DnsClient buildDnsClient(@Nullable DnsServerConfig serverNameResolution) throws SSLException, InterruptedException, FileNotFoundException {
        if (serverNameResolution == null) {
            throw new ConfigurationException("DnsServer not configured (serverNameResolution)");
        }

        DnsClient rawClient;
        DnsType dnsType = serverNameResolution.dnsType();
        if (dnsType == DnsType.LOCAL_OS) {
            rawClient = new RawOsResolver();
        } else if (dnsType == DnsType.LOCAL_NAMESERVER) {
            throw new ConfigurationException("Loading default DNS config not implemented");
            //TODO: load default DNS config
            /*InetAddress dnsServerAddress;
            int port;
            rawClient = new DnsOverUdpClient(dnsServerAddress, port);*/
        } else {
            if (serverNameResolution.host() == null) { throw new ConfigurationException("DNS server host not specified for DnsType " + dnsType); }
            if (serverNameResolution.port() == null) { throw new ConfigurationException("DNS server port not specified for DnsType " + dnsType); }

            InetAddress dnsServerAddress = IpAddressUtil.fromString(serverNameResolution.host());
            int port = serverNameResolution.port();

            if (dnsType == DnsType.DNS_UDP) {
                rawClient = new DnsOverUdpClient(dnsServerAddress, port);
            } else {
                TrustManagerFactory trustManager = ConfigSslContextBuilder.createTrustManagerFactory(serverNameResolution.certificate());
                if (trustManager == null) { trustManager = PkiUtil.getSystemTrustManager(); }
                if (dnsType == DnsType.DNS_TLS) {
                    rawClient = new DnsOverTlsClient(dnsServerAddress, port, trustManager);
                } else {
                    if (serverNameResolution.httpPath() == null) { throw new ConfigurationException("HttpPath not specified for DnsType " + dnsType); }
                    String httpPath = serverNameResolution.httpPath();
                    if (dnsType == DnsType.DNS_HTTPS_1) {
                        rawClient = new DnsOverHttps1Client(dnsServerAddress, port, httpPath, trustManager);
                    } else if (dnsType == DnsType.DNS_HTTPS_2) {
                        rawClient = new DnsOverHttps2Client(dnsServerAddress, port, httpPath, trustManager);
                    } else {
                        throw new ConfigurationException("Unknown DNS type: " + serverNameResolution.dnsType());
                    }
                }
            }
        }

        return new DnsCache(rawClient);
    }
}
