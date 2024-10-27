package com.flower.utils;

import com.google.common.io.Resources;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Enumeration;

public class PkiUtil {
    public static TrustManagerFactory getSystemTrustManager() {
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

    public static TrustManagerFactory getTrustManagerForCertificateResource(String resourceName) {
        try {
            URL resource = Resources.getResource(resourceName);
            InputStream stream = resource.openStream();
            return getTrustManagerForCertificateStream(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrustManagerFactory getTrustManagerForCertificateStream(InputStream certificateStream) {
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

    // --------------------------------------------------

    public static KeyManagerFactory getKeyManagerFromPem(InputStream certificateStream, InputStream keyStream, String keyPassword) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(certificateStream);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PemReader pemReader = new PemReader(new InputStreamReader(keyStream));
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(content);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("myCert", privateKey, keyPassword.toCharArray(), new Certificate[]{cert});

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPassword.toCharArray());

            return keyManagerFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException |
                 InvalidKeySpecException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyStore loadPKCS11KeyStore(String libraryPath, String pin) {
        try {
            // Has to start with `--` to indicate inline config
            String config = String.format("--name = SmartCard\nlibrary = %s\n", libraryPath);

            Provider pkcs11Provider = Security.getProvider("SunPKCS11");
            pkcs11Provider = pkcs11Provider.configure(config);
            Security.addProvider(pkcs11Provider);

            KeyStore pkcs11Store = KeyStore.getInstance("PKCS11", pkcs11Provider);
            pkcs11Store.load(null, pin.toCharArray());

            return pkcs11Store;
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyManagerFactory getKeyManagerFromPKCS11(String libraryPath, String pin) {
        try {
            KeyStore pkcs11Store = loadPKCS11KeyStore(libraryPath, pin);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(pkcs11Store, pin.toCharArray());

            return keyManagerFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static void enumerateKeyStore(KeyStore keyStore, boolean outputKeysInPem, boolean outputCertsInPem) {
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                System.out.println("Alias: " + alias);

                // Check if entry is a key entry
                if (keyStore.isKeyEntry(alias)) {
                    Key key = keyStore.getKey(alias, null);
                    System.out.println("Key Entry: " + key.getAlgorithm());
                    if (outputKeysInPem) {
                        System.out.println(getKeyAsPem(key));
                    }
                }

                // Check if the entry is a certificate entry
                if (keyStore.isKeyEntry(alias) || keyStore.isCertificateEntry(alias)) {
                    Certificate cert = keyStore.getCertificate(alias);
                    System.out.println("Certificate Entry: " + cert.getType());
                    if (outputCertsInPem) {
                        System.out.println(getCertificateAsPem(cert));
                    }
                }
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException |
                 CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getCertificateAsPem(Certificate cert) throws CertificateEncodingException {
        return "-----BEGIN CERTIFICATE-----\n" +
               Base64.getMimeEncoder().encodeToString(cert.getEncoded()) +
               "\n" +
               "-----END CERTIFICATE-----\n";
    }

    public static String getKeyAsPem(Key key) {
        return key.getEncoded() == null ? "Can't form PEM for key - Access Denied" : "-----BEGIN PRIVATE KEY-----\n" +
               Base64.getMimeEncoder().encodeToString(key.getEncoded()) +
               "\n" +
               "-----END PRIVATE KEY-----\n";
    }
}
