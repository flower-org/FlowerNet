package com.flower.crypt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import static com.flower.crypt.PkiUtil.loadPKCS11KeyStore;
import static com.flower.crypt.PkiUtil.loadPKCS11Provider;

public class PKCS11KeyCreator {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        String libraryPath = "/usr/lib/libeToken.so";
        String pin = "Qwerty123";

        Provider pkcs11Provider = loadPKCS11Provider(libraryPath);
        KeyStore pkcs11Store = loadPKCS11KeyStore(pkcs11Provider, pin);

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA", pkcs11Provider);
        keyPairGen.initialize(2048);

        KeyPair keyPair = keyPairGen.generateKeyPair();
//        pkcs11Store.set


        //System.out.println(privateKey);
    }
}
