package com.flower.crypt.keys;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

public interface RsaKeyContext extends KeyContext {
    PublicKey publicKey();
    PrivateKey privateKey();
    X509Certificate certificate();

    static RsaKeyContext of(PublicKey publicKey, PrivateKey privateKey, X509Certificate certificate) {
        return new RsaKeyContext() {
            @Override
            public PublicKey publicKey() {
                return publicKey;
            }

            @Override
            public PrivateKey privateKey() {
                return privateKey;
            }

            @Override
            public X509Certificate certificate() {
                return certificate;
            }
        };
    }
}
