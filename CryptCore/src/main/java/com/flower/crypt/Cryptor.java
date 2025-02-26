package com.flower.crypt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class Cryptor {
    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int AES_KEY_SIZE_BYTES = AES_KEY_SIZE_BITS / 8;
    private static final int IV_LENGTH_BYTES = 16;
    private static final int RSA_KEY_SIZE_BITS = 2048;

    public static byte[] encryptRSAPublic(byte[] data, X509Certificate cert) throws Exception {
        return encryptRSAPublic(data, cert.getPublicKey());
    }

    public static byte[] encryptRSAPublic(byte[] data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    public static byte[] decryptRSAPublic(byte[] encryptedData, X509Certificate cert) throws Exception {
        return decryptRSAPublic(encryptedData, cert.getPublicKey());
    }

    public static byte[] decryptRSAPublic(byte[] encryptedData, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(encryptedData);
    }

    public static byte[] encryptRSAPrivate(byte[] data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    public static byte[] decryptRSAPrivate(byte[] encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedData);
    }

    public static byte[] encryptAESRaw(byte[] data, byte[] key, byte[] iv) throws Exception {
        assert(iv.length == IV_LENGTH_BYTES);
        assert(key.length == AES_KEY_SIZE_BYTES);

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        return encryptAES(data, secretKeySpec, iv);
    }

    public static byte[] decryptAESRaw(byte[] encryptedData, byte[] key, byte[] iv) throws Exception {
        assert(iv.length == IV_LENGTH_BYTES);
        assert(key.length == AES_KEY_SIZE_BYTES);

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        return decryptAES(encryptedData, secretKeySpec, iv);
    }

    public static byte[] encryptAES(byte[] data, SecretKey key, byte[] iv) throws Exception {
        assert(iv.length == IV_LENGTH_BYTES);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivParams = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);

        return cipher.doFinal(data);
    }

    public static byte[] decryptAES(byte[] encryptedData, SecretKey key, byte[] iv) throws Exception {
        assert(iv.length == IV_LENGTH_BYTES);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivParams = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivParams);

        return cipher.doFinal(encryptedData);
    }

    // ----------------------------------------------------------------

    public static byte[] generateAESIV() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static byte[] generateAESKeyRaw() {
        byte[] iv = new byte[AES_KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE_BITS);
        return keyGenerator.generateKey();
    }

    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(RSA_KEY_SIZE_BITS);
        return keyPairGen.generateKeyPair();
    }
}
