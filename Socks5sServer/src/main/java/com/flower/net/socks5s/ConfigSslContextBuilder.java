package com.flower.net.socks5s;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flower.crypt.CertificateToken;
import com.flower.crypt.PkiUtil;
import com.flower.net.config.exception.ConfigurationException;
import com.flower.net.config.pki.CertificateConfig;
import com.flower.net.config.pki.Source;
import com.flower.net.config.serverconf.ServerConfig;
import com.flower.net.socksserver.FlowerSslContextBuilder;
import com.flower.net.utils.VaultRestClient;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static com.flower.net.socks5s.Socks5sServer.DEFAULT_IS_SOCKS5_OVER_TLS;
import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigSslContextBuilder {
    final static Logger LOGGER = LoggerFactory.getLogger(ConfigSslContextBuilder.class);

    @Nullable
    static SslContext buildSslContext(ServerConfig serverConfig)
            throws NoSuchAlgorithmException, CertificateException, SignatureException, InvalidKeyException,
            JsonProcessingException, SSLException, FileNotFoundException {
        boolean isSocks5OverTls = serverConfig.tls() == null ? DEFAULT_IS_SOCKS5_OVER_TLS : serverConfig.tls();
        CertificateConfig certificateCfg = serverConfig.certificate();
        Source clientCertificateCfg = serverConfig.clientCertificate();

        if (!isSocks5OverTls || certificateCfg == null) {
            return null;
        }

        // ## 1. Server Certificate
        KeyManagerFactory keyManager = createKeyManager(certificateCfg);
        // ## 2. Client Certificate
        TrustManagerFactory trustManagerFactory = createTrustManagerFactory(clientCertificateCfg);
        if (keyManager == null && trustManagerFactory == null) {
            return null;
        } else if (keyManager != null && trustManagerFactory != null) {
            return FlowerSslContextBuilder.buildSslContext(keyManager, trustManagerFactory);
        } else {
            return FlowerSslContextBuilder.buildSslContext(keyManager);
        }
    }

    static InputStream getStream(Source certificate) throws FileNotFoundException {
        if (certificate.resourceName() != null) {
            return PkiUtil.getStreamFromResource(certificate.resourceName());
        } else if (certificate.fileName() != null) {
            return new FileInputStream(certificate.fileName());
        } else if (certificate.raw() != null) {
            return new ByteArrayInputStream(certificate.raw().getBytes());
        } else {
            throw new ConfigurationException("Certificate stream not found: " + certificate);
        }
    }

    static @Nullable TrustManagerFactory createTrustManagerFactory(
            @Nullable Source clientCertificateCfg) throws FileNotFoundException {
        // ## 2. Client Certificate
        if (clientCertificateCfg == null) { return null; }

        TrustManagerFactory trustManagerFactory;
        if (clientCertificateCfg.pkcs11LibraryPath() != null) {
            String libraryPath = clientCertificateCfg.pkcs11LibraryPath();
            String pin = clientCertificateCfg.pkcs11pin();
            if (pin == null) {
                throw new ConfigurationException("Client source PKCS#11 pin is null");
            }
            trustManagerFactory = PkiUtil.getTrustManagerFromPKCS11(libraryPath, pin);
        } else {
            InputStream certificateStream = getStream(clientCertificateCfg);
            trustManagerFactory = PkiUtil.getTrustManagerForCertificateStream(certificateStream);
        }
        return trustManagerFactory;
    }

    static KeyManagerFactory createKeyManager(CertificateConfig certificateCfg)
            throws FileNotFoundException, NoSuchAlgorithmException, CertificateException, SignatureException,
            InvalidKeyException, JsonProcessingException {
        // ## 1. Server Certificate
        KeyManagerFactory keyManager;
        if (certificateCfg.generated() != null && certificateCfg.generated()) {
            // Set up TLS with generated self-signed server cert
            LOGGER.info("Initializing with self-signed certificate");

            // 1.1 Generate self-signed server cert
            KeyPair keyPair = PkiUtil.generateRsa2048KeyPair();
            X500Principal subject = new X500Principal("CN=Socks Server Certificate");
            X509Certificate certificate = PkiUtil.generateSelfSignedCertificate(keyPair, subject);

            // 1.2 Try to store a certificate in a wrapped token in Vault
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
                LOGGER.info("\n" + certificateToken.serverData().cert());
            }

            keyManager = PkiUtil.getKeyManagerFromCertAndPrivateKey(certificate, keyPair.getPrivate());
        } else if (certificateCfg.certificate() == null) {
            throw new ConfigurationException("Server source certificate is null");
        } else if (certificateCfg.certificate().pkcs11LibraryPath() != null) {
            String libraryPath = certificateCfg.certificate().pkcs11LibraryPath();
            String pin = certificateCfg.certificate().pkcs11pin();
            if (pin == null) {
                throw new ConfigurationException("Server source PKCS#11 pin is null");
            }
            keyManager = PkiUtil.getKeyManagerFromPKCS11(libraryPath, pin);
        } else {
            InputStream certificateStream = getStream(certificateCfg.certificate());
            if (certificateCfg.privateKey() == null) {
                throw new ConfigurationException("Server certificate private key is null");
            }
            InputStream keyStream = getStream(certificateCfg.privateKey());
            keyManager = PkiUtil.getKeyManagerFromPem(certificateStream, keyStream, "");
        }
        return keyManager;
    }
}
