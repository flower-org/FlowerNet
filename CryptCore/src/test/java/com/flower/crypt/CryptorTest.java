package com.flower.crypt;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CryptorTest {
    private static KeyPair KEY_PAIR;
    private static SecretKey AES_KEY;

    @BeforeAll
    static void setup() throws Exception {
        // Generate RSA key pair
        KEY_PAIR = Cryptor.generateRSAKeyPair();

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
        byte[] iv = Cryptor.generateAESIV();
        byte[] encrypted = Cryptor.encryptAESRaw(plaintext.getBytes(), AES_KEY.getEncoded(), iv);
        byte[] decrypted = Cryptor.decryptAESRaw(encrypted, AES_KEY.getEncoded(), iv);

        assertArrayEquals(plaintext.getBytes(), decrypted, "AES decryption should return the original plaintext");
    }

    @Test
    void testEncryptDecryptAES2() throws Exception {
        String plaintext = "Hello, AES!";
        byte[] iv = Cryptor.generateAESIV();
        byte[] key = Cryptor.generateAESKeyRaw();

        byte[] encrypted = Cryptor.encryptAESRaw(plaintext.getBytes(), key, iv);
        byte[] decrypted = Cryptor.decryptAESRaw(encrypted, key, iv);

        assertArrayEquals(plaintext.getBytes(), decrypted, "AES decryption should return the original plaintext");
    }
}
