package com.flower.crypt;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Enumeration;

import static com.google.common.base.Preconditions.checkNotNull;

public class Pkcs11CsrSigner {
    public static void printHelp() {
        System.out.println("Params: reqFile certAlias pin (OPT)libraryPath");
    }

    public static void main(String[] args) throws IOException, KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException, CertificateEncodingException {
        String reqFile;
        if (args.length > 0) { reqFile = args[0]; } else { printHelp(); return; }

        String certAlias;
        if (args.length > 1) { certAlias = args[1]; } else { printHelp(); return; }

        String pin;
        if (args.length > 2) { pin = args[2]; } else { printHelp(); return; }

        String libraryPath = "/usr/lib/libeToken.so";
        if (args.length > 3) { libraryPath = args[3]; }

        KeyStore keyStore = PkiUtil.loadPKCS11KeyStore(libraryPath, pin);

        PrivateKey key = null;
        X509Certificate cert = null;

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.out.println("Alias: " + alias);

            if (!alias.equals(certAlias)) {
                continue;
            }

            // Check if entry is a key entry
            if (keyStore.isKeyEntry(alias)) {
                key = (PrivateKey)keyStore.getKey(alias, null);
                cert = (X509Certificate)keyStore.getCertificate(alias);
                break;
            }
        }

        File csrFile = new File(reqFile);
        PEMParser pemParser = new PEMParser(new FileReader(csrFile));
        PKCS10CertificationRequest csr = (PKCS10CertificationRequest)pemParser.readObject();
        pemParser.close();

        X509Certificate signedCert = signCsr(csr, checkNotNull(key), checkNotNull(cert));

        String certificateStr = PkiUtil.getCertificateAsPem(signedCert);
        System.out.println(certificateStr);
    }

    static X509Certificate signCsr(PKCS10CertificationRequest csr,
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
}
