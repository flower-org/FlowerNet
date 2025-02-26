package com.flower.crypt;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class UntrustingTrustManagerFactory extends TrustManagerFactory {
    private static final TrustManager[] UNTRUSTING_MANAGERS = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    throw new CertificateException("Fuck you");
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    throw new CertificateException("Fuck you");
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    private static class UntrustingTrustManagerFactorySpi extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore keyStore) {
            // no init
        }

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
            // no init
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return UNTRUSTING_MANAGERS;
        }
    }

    public UntrustingTrustManagerFactory() {
        super(new UntrustingTrustManagerFactorySpi(), null, "Untrusting");
    }
}
