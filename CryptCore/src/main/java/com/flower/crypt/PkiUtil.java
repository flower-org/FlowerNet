package com.flower.crypt;

import com.google.common.io.Resources;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;

public class PkiUtil {
    private static final int FILE_ENCRYPT_CHUNK_SIZE = 4096;
    private static final int FILE_SIGNATURE_CHUNK_SIZE = 4096;
    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";

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
        return getTrustManagerForCertificateStream(getStreamFromResource(resourceName));
    }

    public static InputStream getStreamFromResource(String resourceName) {
        try {
            URL resource = Resources.getResource(resourceName);
            return resource.openStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrustManagerFactory getTrustManagerFromPKCS11(String libraryPath, String pin) {
        KeyStore pkcs11Store = loadPKCS11KeyStore(libraryPath, pin);
        return getTrustManagerForKeyStore(pkcs11Store);
    }

    public static X509Certificate getCertificateFromResource(String resourceName) {
        return getCertificateFromStream(getStreamFromResource(resourceName));
    }

    public static X509Certificate getCertificateFromStream(InputStream certificateStream) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(certificateStream);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509Certificate getCertificateFromString(String certStr) {
        return getCertificateFromStream(new ByteArrayInputStream(certStr.getBytes()));
    }

    public static PrivateKey getPrivateKeyFromStream(InputStream keyStream) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PemReader pemReader = new PemReader(new InputStreamReader(keyStream));
        PemObject pemObject = pemReader.readPemObject();
        byte[] content = pemObject.getContent();
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(content);
        return keyFactory.generatePrivate(privateKeySpec);
    }

    public static PrivateKey getPrivateKeyFromString(String keyStr) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        return getPrivateKeyFromStream(new ByteArrayInputStream(keyStr.getBytes()));
    }

    public static KeyStore loadTrustStore(X509Certificate cert) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("trustedCert", cert);
            return keyStore;
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyStore loadTrustStore(InputStream certificateStream) {
        X509Certificate cert = getCertificateFromStream(certificateStream);
        return loadTrustStore(cert);
    }

    public static KeyStore loadTrustStore(String resourceName) {
        X509Certificate cert = getCertificateFromStream(getStreamFromResource(resourceName));
        return loadTrustStore(cert);
    }

    public static KeyStore loadTrustStore(File file) throws FileNotFoundException {
        X509Certificate cert = getCertificateFromStream(new FileInputStream(file));
        return loadTrustStore(cert);
    }

    public static TrustManagerFactory getTrustManagerForCertificateStream(InputStream certificateStream) {
        KeyStore keyStore = loadTrustStore(certificateStream);
        return getTrustManagerForKeyStore(keyStore);
    }

    public static TrustManagerFactory getTrustManagerForKeyStore(KeyStore keyStore) {
        try {
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
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    // --------------------------------------------------

    public static KeyManagerFactory getKeyManagerFromResources(String certResourceName, String keyResourceName, String keyPassword) {
        InputStream cerStream = getStreamFromResource(certResourceName);
        InputStream keyStream = getStreamFromResource(keyResourceName);
        return getKeyManagerFromPem(cerStream, keyStream, keyPassword);
    }

    public static KeyManagerFactory getKeyManagerFromPem(InputStream certificateStream, InputStream keyStream, String keyPassword) {
        try {
            X509Certificate cert = getCertificateFromStream(certificateStream);
            PrivateKey privateKey = getPrivateKeyFromStream(keyStream);

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

    public static KeyManagerFactory getKeyManagerFromCertAndPrivateKey(X509Certificate cert, PrivateKey privateKey) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("myCert", privateKey, null, new Certificate[]{cert});

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, null);

            return keyManagerFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static Provider loadPKCS11Provider(String libraryPath) {
        // Has to start with `--` to indicate inline config
        String config = String.format("--name = SmartCard\nlibrary = %s\n", libraryPath);

        Provider pkcs11Provider = Security.getProvider("SunPKCS11");
        pkcs11Provider = pkcs11Provider.configure(config);
        Security.addProvider(pkcs11Provider);

        return pkcs11Provider;
    }

    public static KeyStore loadPKCS11KeyStore(String libraryPath, String pin) {
        Provider pkcs11Provider = loadPKCS11Provider(libraryPath);
        return loadPKCS11KeyStore(pkcs11Provider, pin);
    }

    public static KeyStore loadPKCS11KeyStore(Provider pkcs11Provider, String pin) {
        try {
            //Pretty notable is the fact is that if we use `getInstance(String type, String provider)`
            // removing and reinserting USB token is not recoverable without program restart.
            // Also, you can't re-attach USB token after first load, if it fails due to no token attached.
            // On the other hand, calling `getInstance(String type)` again reloads correctly.
            KeyStore pkcs11Store = KeyStore.getInstance("PKCS11");
            //KeyStore pkcs11Store = KeyStore.getInstance("PKCS11", pkcs11Provider);
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
            keyManagerFactory.init(pkcs11Store, null);

            return keyManagerFactory;
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static Key getKeyFromKeyStore(KeyStore keyStore, String alias) {
        try {
            return keyStore.getKey(alias, null);
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static Certificate getCertificateFromKeyStore(KeyStore keyStore, String alias) {
        try {
            return keyStore.getCertificate(alias);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getKeyAliasesFromKeyStore(KeyStore keyStore) {
        List<String> keyAliases = new ArrayList<>();
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    keyAliases.add(alias);
                }
            }
            return keyAliases;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getCertificateAliasesFromKeyStore(KeyStore keyStore) {
        List<String> certificateAliases = new ArrayList<>();
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias) || keyStore.isCertificateEntry(alias)) {
                    certificateAliases.add(alias);
                }
            }
            return certificateAliases;
        } catch (KeyStoreException e) {
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
        return cert.getEncoded() == null ? "Can't form PEM for certificate - Access Denied"
                :
               "-----BEGIN CERTIFICATE-----\n" +
               Base64.getMimeEncoder().encodeToString(cert.getEncoded()) +
               "\n" +
               "-----END CERTIFICATE-----\n";
    }

    public static String getKeyAsPem(Key key) {
        return key.getEncoded() == null ? "Can't form PEM for key - Access Denied"
                :
               "-----BEGIN PRIVATE KEY-----\n" +
               Base64.getMimeEncoder().encodeToString(key.getEncoded()) +
               "\n" +
               "-----END PRIVATE KEY-----\n";
    }

    public static String printSessionCertificates(SSLSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("Local certificates:\n");
        Certificate[] localCerts = session.getLocalCertificates();
        if (localCerts != null) {
            for (Certificate certificate : localCerts) {
                builder.append(certificate).append("\n");
            }
        }
        try {
            Certificate[] peerCerts = session.getPeerCertificates();
            builder.append("Peer certificates:\n");
            if (peerCerts != null) {
                for (Certificate certificate : peerCerts) {
                    builder.append(certificate).append("\n");
                }
            }
        } catch (SSLPeerUnverifiedException e) {
            // One-way SSL
        }

        return builder.toString();
    }

    public static void saveCertificateToPemFile(X509Certificate certificate, File saveToFile) throws IOException {
        JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(saveToFile));
        pemWriter.writeObject(certificate);
    }

    /** @return true if cer1 was signed by caCer */
    public static boolean verifyCertificateSignature(X509Certificate cer1, X509Certificate caCer) {
        PublicKey caPublicKey = caCer.getPublicKey();
        try {
            cer1.verify(caPublicKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static KeyPair generateRsa2048KeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, X500Principal subject) throws CertificateException {
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + (365L *24*60*60*1000));

        BigInteger serialNumber = new BigInteger(160, new SecureRandom());
        X509v3CertificateBuilder certificateBuilder =
                new JcaX509v3CertificateBuilder(subject, serialNumber, startDate, endDate, subject, keyPair.getPublic());

        final ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        } catch (OperatorCreationException e) {
            throw new CertificateException(e);
        }
        return new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));
    }

    public static String signData(String dataString, PrivateKey privateKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        byte[] signedData = signData(dataString.getBytes(), privateKey);
        return Base64.getEncoder().encodeToString(signedData);
    }

    public static boolean verifySignature(String dataString, String sign, PublicKey publicKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        byte[] decodedSignature = Base64.getDecoder().decode(sign);
        return verifySignature(dataString.getBytes(), decodedSignature, publicKey);
    }

    public static String encrypt(String plaintext, PublicKey publicKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        byte[] encryptedBytes = encrypt(plaintext.getBytes(), publicKey);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedText, PrivateKey privateKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        byte[] decryptedBytes = decrypt(Base64.getDecoder().decode(encryptedText), privateKey);
        return new String(decryptedBytes);
    }

    public static String encrypt(String plaintext, PrivateKey privateKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        byte[] encryptedBytes = encrypt(plaintext.getBytes(), privateKey);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedText, PublicKey publicKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        byte[] decryptedBytes = decrypt(Base64.getDecoder().decode(encryptedText), publicKey);
        return new String(decryptedBytes);
    }

    public static byte[] encrypt(byte[] data, PublicKey publicKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] encryptedData, PrivateKey privateKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedData);
    }

    public static byte[] encrypt(byte[] data, PrivateKey privateKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] encryptedData, PublicKey publicKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(encryptedData);
    }

    public static byte[] signData(byte[] data, PrivateKey privateKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        Signature signature = Signature.getInstance("SHA256WithRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    public static byte[] signData(File file, PrivateKey privateKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);

        // Read the file in chunks
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[FILE_SIGNATURE_CHUNK_SIZE]; // 4KB buffer
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                signature.update(buffer, 0, bytesRead);
            }
        }

        return signature.sign();
    }

    public static boolean verifySignature(byte[] data, byte[] sign, PublicKey publicKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        Signature signature = Signature.getInstance("SHA256WithRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(sign);
    }

    public static boolean verifySignature(File file, byte[] signatureBytes, PublicKey publicKey) throws InvalidKeyException, IOException, SignatureException, NoSuchAlgorithmException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);

        // Read the file in chunks
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096]; // 4KB buffer
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                signature.update(buffer, 0, bytesRead);
            }
        }

        return signature.verify(signatureBytes);
    }

    public static boolean verifySignature(File file, File signatureFile, PublicKey publicKey) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        byte[] sign = Files.readAllBytes(signatureFile.toPath());
        return verifySignature(file, sign, publicKey);
    }

    public static boolean testKeyPairMatchBySigning(PublicKey publicKey, PrivateKey privateKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        byte[] data = "Test data".getBytes();
        byte[] signature = signData(data, privateKey);
        return verifySignature(data, signature, publicKey);
    }

    public static boolean testKeyPairMatchByEncrypting(PublicKey publicKey, PrivateKey privateKey) throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        byte[] data = "Test data".getBytes();

        byte[] encryptedData = encrypt(data, publicKey);
        byte[] decryptedData = decrypt(encryptedData, privateKey);

        return new String(decryptedData).equals(new String(data));
    }

    public static byte[] getSha256(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(bytes);

    }

    public static String getSha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(getSha256(bytes));
    }

    public static byte[] getSha256(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(file)) {
            DigestInputStream dis = new DigestInputStream(fis, digest);
            while (dis.read() != -1) {
                // Here we're reading file stream to update hash
            }
        }
        return digest.digest();
    }

    public static String getSha256Hex(File file) throws NoSuchAlgorithmException, IOException {
        return HexFormat.of().formatHex(getSha256(file));
    }

    public static String toHex(byte[] bytes) throws NoSuchAlgorithmException, IOException {
        return HexFormat.of().formatHex(bytes);
    }

    public static X509Certificate signCsr(PKCS10CertificationRequest csr,
                                          PrivateKey caPrivateKey,
                                          X509Certificate caCert) {
        try {
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000 * 10)); //10 years

            BigInteger serialNumber = new BigInteger(64, new SecureRandom());

            PublicKey subjectPublicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(csr.getSubjectPublicKeyInfo().getEncoded()));

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    new X500Name(caCert.getSubjectX500Principal().getName()),
                    serialNumber,
                    notBefore,
                    notAfter,
                    csr.getSubject(),
                    subjectPublicKey
            );

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caPrivateKey);
            X509CertificateHolder certHolder = certBuilder.build(signer);

            return new JcaX509CertificateConverter().getCertificate(certHolder);
        } catch (CertificateException | OperatorCreationException | NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static PKCS10CertificationRequest loadCsr(File f) throws IOException {
        return loadCsr(new FileReader(f));
    }

    public static PKCS10CertificationRequest loadCsr(String csr) throws IOException {
        return loadCsr(new StringReader(csr));
    }

    public static PKCS10CertificationRequest loadCsr(Reader reader) throws IOException {
        PEMParser pemParser = new PEMParser(reader);
        return (PKCS10CertificationRequest)pemParser.readObject();
    }

    public static void encryptFile(SecretKeySpec secretKey, IvParameterSpec iv, FileInputStream fis,
                                   FileOutputStream fos, int length) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, IOException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);

        byte[] inputBytes = new byte[FILE_ENCRYPT_CHUNK_SIZE];
        int bytesRead;
        int totalBytesRead = 0;

        // Read and encrypt in chunks until the specified length is reached
        while (totalBytesRead < length && (bytesRead = fis.read(inputBytes)) != -1) {
            // Only process the bytes up to the specified length
            int bytesToProcess = Math.min(bytesRead, length - totalBytesRead);
            byte[] outputBytes = cipher.update(inputBytes, 0, bytesToProcess);
            if (outputBytes != null) {
                fos.write(outputBytes);
            }
            totalBytesRead += bytesToProcess;
        }

        // Finalize encryption if we have processed any bytes
        if (totalBytesRead > 0) {
            byte[] outputBytes = cipher.doFinal();
            if (outputBytes != null) {
                fos.write(outputBytes);
            }
        }
    }

    public static void decryptFile(SecretKeySpec secretKey, IvParameterSpec iv, FileInputStream fis,
                                   FileOutputStream fos, int length) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, IOException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

        byte[] inputBytes = new byte[FILE_ENCRYPT_CHUNK_SIZE]; // Buffer size of 4KB
        int bytesRead;
        int totalBytesRead = 0;

        // Read and decrypt in chunks until the specified length is reached
        while (totalBytesRead < length && (bytesRead = fis.read(inputBytes)) != -1) {
            // Only process the bytes up to the specified length
            int bytesToProcess = Math.min(bytesRead, length - totalBytesRead);
            byte[] outputBytes = cipher.update(inputBytes, 0, bytesToProcess);
            if (outputBytes != null) {
                fos.write(outputBytes);
            }
            totalBytesRead += bytesToProcess;
        }

        // Finalize decryption
        if (totalBytesRead > 0) {
            // If we have processed any bytes, finalize the decryption
            byte[] outputBytes = cipher.doFinal(); // This handles padding
            if (outputBytes != null) {
                fos.write(outputBytes);
            }
        }
    }
}
