package com.flower.net.socks5s;

import com.flower.net.dns.DnsClient;
import com.flower.net.dns.cache.DnsCache;
import com.flower.net.dns.client.dnsoverhttps2.DnsOverHttps2Client;
import com.flower.net.socksserver.FlowerSslContextBuilder;
import com.flower.net.socksserver.SocksServer;
import com.flower.net.utils.IpAddressUtil;
import com.flower.crypt.PkiUtil;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.flower.net.config.CertificateToken;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import com.flower.net.utils.VaultRestClient;

public final class Socks5sServer {
    final static Logger LOGGER = LoggerFactory.getLogger(Socks5sServer.class);

    static final boolean DEFAULT_IS_SOCKS5_OVER_TLS = true;
    static final boolean DEFAULT_SELF_GENERATE_CERT = true;
    static final int DEFAULT_PORT = 8443;
    static final boolean ALLOW_DIRECT_IP_ACCESS = true;

    public static void main(String[] args) throws Exception {
        boolean isSocks5OverTls = DEFAULT_IS_SOCKS5_OVER_TLS;
        boolean isSelfGenerateCert = DEFAULT_SELF_GENERATE_CERT;
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            isSocks5OverTls = Boolean.parseBoolean(args[0]);
        }
        if (args.length > 1) {
            isSelfGenerateCert = Boolean.parseBoolean(args[1]);
        }
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        }

        final DnsClient dnsClient;
        {
            final int dnsOverHttpsServerPort = 443;
            final InetAddress dnsServerAddress = IpAddressUtil.fromString("1.1.1.1");
            final String dnsServerPathPrefix = "/dns-query?name=";
            final TrustManagerFactory trustManager = PkiUtil.getTrustManagerForCertificateResource("oneone_cert.pem");
            dnsClient = new DnsCache(new DnsOverHttps2Client(dnsServerAddress, dnsOverHttpsServerPort, dnsServerPathPrefix, trustManager));
        }

        SslContext sslCtx;
        if (isSocks5OverTls) {
            if (isSelfGenerateCert) {
                // Set up TLS with generated self-signed server cert
                LOGGER.info("Initializing with self-signed certificate");

                // 1. Generate self-signed server cert
                KeyPair keyPair = PkiUtil.generateRsa2048KeyPair();
                X500Principal subject = new X500Principal("CN=Socks Server Certificate");
                X509Certificate certificate = PkiUtil.generateSelfSignedCertificate(keyPair, subject);

                // 2. Try to store a certificate in a wrapped token in Vault
                String podName = System.getenv("HOSTNAME");
                CertificateToken certificateToken = CertificateToken.createToken(certificate, keyPair.getPrivate(), podName);
                String certificateTokenStr = CertificateToken.createTokenString(certificateToken);
                try {
                    String vaultToken = VaultRestClient.kubernetesAuth();
                    String wrappedToken = VaultRestClient.createWrappedToken(vaultToken, certificateTokenStr);
                    LOGGER.info("Wrapped token created (900)\n{}", wrappedToken);
                } catch(Exception e) {
                    LOGGER.info("Failed to save wrapped token", e);
                    LOGGER.info("Outputting raw certificate token\n{}", certificateTokenStr);
                    System.out.println(certificateToken.serverData().cert());
                }

                KeyManagerFactory keyManager = PkiUtil.getKeyManagerFromCertAndPrivateKey(certificate, keyPair.getPrivate());
                sslCtx = FlowerSslContextBuilder.buildSslContext(keyManager);
            } else {
                // Set up TLS with embedded server cert
                LOGGER.info("Initializing with embedded certificate (resources)");
                sslCtx = FlowerSslContextBuilder.buildSslContext();
            }
        } else {
            sslCtx = null;
        }

        SocksServer server = new SocksServer(() -> ALLOW_DIRECT_IP_ACCESS, () -> new SocksServerConnectHandler(dnsClient));
        try {
            LOGGER.info("Starting on port {} TLS: {}", port, isSocks5OverTls);
            server.startServer(port, sslCtx)
                    .sync().channel().closeFuture().sync();
        } finally {
            server.shutdownServer();
        }
    }
}
