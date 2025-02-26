package com.flower.crypt;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

public class WhatsOnMySafeNetToken {
    public static void main(String[] args) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        String libraryPath = "/usr/lib/libeToken.so";
        String pin = "mypin";
        KeyStore keyStore = PkiUtil.loadPKCS11KeyStore(libraryPath, pin);
        PkiUtil.enumerateKeyStore(keyStore, true, true);
    }
}
