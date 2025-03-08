package com.flower.net.sockschain.server;

import com.flower.crypt.PkiUtil;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

//TODO: replace with config
class ETokenKeyManagerProvider {
    @Nullable private static KeyManagerFactory PKCS11_KEY_MANAGER = null;
    private static ReentrantLock LOCK = new ReentrantLock();

    public static KeyManagerFactory getManagerOld() {
        if (PKCS11_KEY_MANAGER == null) {
            try {
                LOCK.lock();
                if (PKCS11_KEY_MANAGER == null) {
                    PKCS11_KEY_MANAGER = PkiUtil.getKeyManagerFromPKCS11("/usr/lib/libeToken.so", "Qwerty123");
                }
            } finally {
                LOCK.unlock();
            }
        }
        return checkNotNull(PKCS11_KEY_MANAGER);
    }
}
