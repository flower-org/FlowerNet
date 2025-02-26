package com.flower.crypt;

import org.junit.jupiter.api.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PkiUtilTest {
    @Test
    public void testSignatures() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = PkiUtil.generateRsa2048KeyPair();

        String data = "MyData";

        String sign = PkiUtil.signData(data, keyPair.getPrivate());
        boolean verified = PkiUtil.verifySignature(data, sign, keyPair.getPublic());

        assertTrue(verified);
    }

    @Test
    public void testRsaEncryption()
            throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException {
        KeyPair keyPair = PkiUtil.generateRsa2048KeyPair();

        String data = "MyData";

        String encrypted1 = PkiUtil.encrypt(data, keyPair.getPrivate());
        assertEquals(data, PkiUtil.decrypt(encrypted1, keyPair.getPublic()));

        String encrypted2 = PkiUtil.encrypt(data, keyPair.getPublic());
        assertEquals(data, PkiUtil.decrypt(encrypted2, keyPair.getPrivate()));
    }
}
