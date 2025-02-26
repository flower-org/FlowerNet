package com.flower.net.sockschain.client;

import com.flower.net.utils.PkiUtil;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

public class ETokenKeyManagerProvider {
    @Nullable private static KeyManagerFactory PKCS11_KEY_MANAGER = null;
    private static ReentrantLock LOCK = new ReentrantLock();

    static KeyManagerFactory getManager() {
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
