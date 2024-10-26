package com.flower.utils;

import com.google.common.io.Resources;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class ServerUtil {
    public static InetAddress getByName(String name) {
        try {
            return InetAddress.getByName(name);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrustManagerFactory getTrustedCertificates() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore)null);

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            return trustManagerFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrustManagerFactory getFromCertificateResource(String resourceName) {
        try {
            URL resource = Resources.getResource(resourceName);
            InputStream stream = resource.openStream();
            return getFromCertificate(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrustManagerFactory getFromCertificate(InputStream certificateStream) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(certificateStream);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("trustedCert", cert);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            X509TrustManager trustManager = null;
            for (TrustManager tm : trustManagers) {
                if (tm instanceof X509TrustManager) {
                    trustManager = (X509TrustManager) tm;
                    break;
                }
            }

            if (trustManager == null) {
                throw new IllegalStateException("Cert not loaded - X509TrustManager not found");
            }

            return trustManagerFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrustManagerFactory getUntrustingManager() {
        return new UntrustingTrustManagerFactory();
    }
}

