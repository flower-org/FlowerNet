package com.flower.pkitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CryptorTest {
    private static KeyPair KEY_PAIR;
    private static SecretKey AES_KEY;

    @BeforeAll
    static void setup() throws Exception {
        // Generate RSA key pair
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KEY_PAIR = keyPairGen.generateKeyPair();

        // Generate AES key
        AES_KEY = Cryptor.generateAESKey();
    }

    @Test
    void testEncryptDecryptRSA() throws Exception {
        String plaintext = "Hello, RSA!";
        byte[] encrypted = Cryptor.encryptRSAPublic(plaintext.getBytes(), KEY_PAIR.getPublic());
        byte[] decrypted = Cryptor.decryptRSAPrivate(encrypted, KEY_PAIR.getPrivate());

        assertArrayEquals(plaintext.getBytes(), decrypted, "RSA decryption should return the original plaintext");
    }

    @Test
    void testEncryptDecryptRSAPrivate() throws Exception {
        String plaintext = "Hello, RSA Private Encryption!";
        byte[] encrypted = Cryptor.encryptRSAPrivate(plaintext.getBytes(), KEY_PAIR.getPrivate());
        byte[] decrypted = Cryptor.decryptRSAPublic(encrypted, KEY_PAIR.getPublic());

        assertArrayEquals(plaintext.getBytes(), decrypted, "RSA public key should decrypt private key encrypted data");
    }

    @Test
    void testEncryptDecryptAES() throws Exception {
        String plaintext = "Hello, AES!";
        byte[] iv = Cryptor.generateIV();
        byte[] encrypted = Cryptor.encrypt(plaintext.getBytes(), iv, AES_KEY);

        // Extract IV from encrypted data
        byte[] extractedIV = Arrays.copyOfRange(encrypted, 0, 16);
        byte[] encryptedData = Arrays.copyOfRange(encrypted, 16, encrypted.length);

        byte[] decrypted = Cryptor.decrypt(encryptedData, AES_KEY.getEncoded(), extractedIV);

        assertArrayEquals(plaintext.getBytes(), decrypted, "AES decryption should return the original plaintext");
    }
}
