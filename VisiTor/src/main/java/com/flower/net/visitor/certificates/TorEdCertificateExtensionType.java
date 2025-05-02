package com.flower.net.visitor.certificates;

public enum TorEdCertificateExtensionType {
    //[04] - signed-with-ed25519-key
    SIGNED_WITH_ED25519_KEY(0x04);

    public final int code;
    TorEdCertificateExtensionType(int code) {
        this.code = code;
    }

    public static TorEdCertificateExtensionType fromCode(int code) {
        switch(code) {
            case 0x04: return SIGNED_WITH_ED25519_KEY;
            default: throw new UnsupportedOperationException("TorEdCertificateExtensionType code:" + code + " unknown");
        }
    }
}
