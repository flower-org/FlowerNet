package com.flower.pkitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CryptorTest {
    private static KeyPair keyPair;
    private static SecretKey aesKey;

    @BeforeAll
    static void setup() throws Exception {
        // Generate RSA key pair
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        keyPair = keyPairGen.generateKeyPair();

        // Generate AES key
        aesKey = Cryptor.generateAESKey();
    }

    @Test
    void testEncryptDecryptRSA() throws Exception {
        String plaintext = "Hello, RSA!";
        byte[] encrypted = Cryptor.encryptRSAPublic(plaintext.getBytes(), keyPair.getPublic());
        byte[] decrypted = Cryptor.decryptRSAPrivate(encrypted, keyPair.getPrivate());

        assertArrayEquals(plaintext.getBytes(), decrypted, "RSA decryption should return the original plaintext");
    }

    @Test
    void testEncryptDecryptRSAPrivate() throws Exception {
        String plaintext = "Hello, RSA Private Encryption!";
        byte[] encrypted = Cryptor.encryptRSAPrivate(plaintext.getBytes(), keyPair.getPrivate());
        byte[] decrypted = Cryptor.decryptRSAPublic(encrypted, keyPair.getPublic());

        assertArrayEquals(plaintext.getBytes(), decrypted, "RSA public key should decrypt private key encrypted data");
    }

    @Test
    void testEncryptDecryptAES() throws Exception {
        String plaintext = "Hello, AES!";
        byte[] iv = Cryptor.generateIV();
        byte[] encrypted = Cryptor.encrypt(plaintext.getBytes(), iv, aesKey);

        // Extract IV from encrypted data
        byte[] extractedIV = Arrays.copyOfRange(encrypted, 0, 16);
        byte[] encryptedData = Arrays.copyOfRange(encrypted, 16, encrypted.length);

        byte[] decrypted = Cryptor.decrypt(encryptedData, aesKey.getEncoded(), extractedIV);

        assertArrayEquals(plaintext.getBytes(), decrypted, "AES decryption should return the original plaintext");
    }
}
