package com.flower.net.visitor.certificates;

public enum TorCertificateKeyType {
    /** [01]: ed25519 key */
    ED25519(0x01),
    /** [02]: SHA256(DER(key)) for an RSA key. (Not currently used.) */
    SHA256_DER_KEY_FOR_RSA(0x02),
    /** [03]: SHA-256 digest of an X.509 certificate. (Used with certificate type 5 (TLS Link certificate).) */
    SHA_256_DIGEST_OF_X509(0x03);

    public final int code;
    TorCertificateKeyType(int code) {
        this.code = code;
    }

    public static TorCertificateKeyType fromCode(int code) {
        switch(code) {
            case 0x01: return ED25519;
            case 0x02: return SHA256_DER_KEY_FOR_RSA;
            case 0x03: return SHA_256_DIGEST_OF_X509;
            default: throw new UnsupportedOperationException("TorCertificateKeyType code:" + code + " unknown");
        }
    }
}
