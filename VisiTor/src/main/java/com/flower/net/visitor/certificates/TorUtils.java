package com.flower.net.visitor.certificates;

import org.bouncycastle.asn1.pkcs.RSAPublicKey;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

public class TorUtils {
    public static final int FIXED_CELL_LEN = 512;
    public static final int FIXED_CELL_BODY_LEN = 509;

    public static long toUInt32BigEndian(byte[] byteArrayOf4) {
        return toUInt32BigEndian(byteArrayOf4, 0);
    }

    public static long toUInt32BigEndian(byte[] byteArray, int offset) {
        // Convert byte array to unsigned int
        return ((byteArray[offset] & 0xFF) << 24) |
                ((byteArray[offset + 1] & 0xFF) << 16) |
                ((byteArray[offset + 2] & 0xFF) << 8) |
                (byteArray[offset + 3] & 0xFF);
    }

    public static int toUInt16BigEndian(byte[] byteArrayOf2) {
        return toUInt16BigEndian(byteArrayOf2, 0);
    }

    public static int toUInt16BigEndian(byte[] byteArray, int offset) {
        // Convert byte array to unsigned int
        return ((byteArray[offset] & 0xFF) << 8) |
                (byteArray[offset + 1] & 0xFF);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02X", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static byte[] getCertificateSHA256Digest(Certificate certificate) {
        try {
            byte[] encodedCert = certificate.getEncoded();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(encodedCert);
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] getKeySHA256Digest(PublicKey key) {
        try {
            byte[] rawKeyBytes = getPKCS1Encoded(key);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(rawKeyBytes);
            return digest.digest();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] getPKCS1Encoded(PublicKey publicKey) throws IOException {
        if (!(publicKey instanceof java.security.interfaces.RSAPublicKey)) {
            throw new IllegalArgumentException("Public key is not an RSA key");
        }

        java.security.interfaces.RSAPublicKey rsaPublicKey = (java.security.interfaces.RSAPublicKey) publicKey;
        BigInteger modulus = rsaPublicKey.getModulus();
        BigInteger publicExponent = rsaPublicKey.getPublicExponent();

        // Create the PKCS#1 structure
        RSAPublicKey pkcs1Key = new RSAPublicKey(modulus, publicExponent);
        return pkcs1Key.getEncoded();
    }
}
