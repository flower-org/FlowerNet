package com.flower.crypt.keys;

import javax.net.ssl.KeyManagerFactory;

public interface RsaKeyProvider extends KeyProvider {
    KeyManagerFactory getKeyManagerFactory();
}
