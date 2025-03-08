package com.flower.crypt.keys;

import javax.annotation.Nullable;

public interface Aes256KeyContext extends KeyContext {
    byte[] aes256Key();
    @Nullable byte[] aes256Iv();

    static Aes256KeyContext of(byte[] aes256Key, @Nullable byte[] aes256Iv) {
        return new Aes256KeyContext() {
            @Override
            public byte[] aes256Key() {
                return aes256Key;
            }

            @Override
            @Nullable public byte[] aes256Iv() {
                return aes256Iv;
            }
        };
    }
}
