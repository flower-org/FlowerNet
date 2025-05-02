package com.flower.net.visitor.certificates;

import java.util.Set;

/**
 * Note 1: The certificate types [09] HS_IP_V_SIGNING and [0B] HS_IP_CC_SIGNING were implemented incorrectly,
 * and now cannot be changed. Their signing keys and subject keys, as implemented, are given in the table.
 * They were originally meant to be the inverse of this order.
 */
public enum TorCertificateType {
    /** Legacy channel negotiation (Obsolete) */
    TLS_LINK_X509(0x01), //X.509 KP_legacy_conn_tls KS_relayid_rsa
    /** Legacy channel negotiation (Obsolete) */
    RSA_ID_X509(0x02), //X.509 KP_relayid_rsa KS_relayid_rsa
    /** Legacy channel negotiation (Obsolete) */
    LINK_AUTH_X509(0x03), //X.509 KP_legacy_linkauth_rsa KS_relayid_rsa
    /** Online signing keys */
    IDENTITY_V_SIGNING(0x04), //Ed KP_relaysign_ed KS_relayid_ed
    /** TLS Link certificate */
    SIGNING_V_TLS_CERT(0x05), //Ed A TLS certificate KS_relaysign_ed CERTS cells
    SIGNING_V_LINK_AUTH(0x06),	//Ed KP_link_ed KS_relaysign_ed CERTS cells
    /** RSA Identity cross verification */
    RSA_ID_V_IDENTITY(0x07), //Rsa KP_relayid_ed KS_relayid_rsa CERTS cells
    BLINDED_ID_V_SIGNING(0x08), //Ed KP_hs_desc_sign KS_hs_blind_id HsDesc (outer)
    /** Backwards, see note 1 */
    HS_IP_V_SIGNING(0x09), //Ed KP_hs_ipt_sid KS_hs_desc_sign HsDesc (auth-key)
    NTOR_CC_IDENTITY(0x0A), //Ed KP_relayid_ed EdCvt(KS_ntor) ntor cross-cert
    /** Backwards, see note 1 */
    HS_IP_CC_SIGNING(0x0B), //Ed KP_hss_ntor KS_hs_desc_sign HsDesc (enc-key-cert)
    FAMILY_V_IDENTITY(0x0C); //Ed KP_relayid_ed KS_familyid_ed family-cert

    static final Set<TorCertificateType> X509_CERT_TYPES = Set.of(TLS_LINK_X509, RSA_ID_X509, LINK_AUTH_X509);
    static final Set<TorCertificateType> ED_CERT_TYPES = Set.of(IDENTITY_V_SIGNING, SIGNING_V_TLS_CERT,
            SIGNING_V_LINK_AUTH, BLINDED_ID_V_SIGNING, HS_IP_V_SIGNING, NTOR_CC_IDENTITY, HS_IP_CC_SIGNING,
            FAMILY_V_IDENTITY);
    static final Set<TorCertificateType> RSA_ED_CROSS_CERT_TYPES = Set.of(RSA_ID_V_IDENTITY);

    public final int code;
    TorCertificateType(int code) {
        this.code = code;
    }

    public static TorCertificateType fromCode(int code) {
        switch(code) {
            case 0x01: return TLS_LINK_X509;
            case 0x02: return RSA_ID_X509;
            case 0x03: return LINK_AUTH_X509;

            case 0x04: return IDENTITY_V_SIGNING;
            case 0x05: return SIGNING_V_TLS_CERT;
            case 0x06: return SIGNING_V_LINK_AUTH;

            case 0x07: return RSA_ID_V_IDENTITY;

            case 0x08: return BLINDED_ID_V_SIGNING;
            case 0x09: return HS_IP_V_SIGNING;
            case 0x0A: return NTOR_CC_IDENTITY;
            case 0x0B: return HS_IP_CC_SIGNING;
            case 0x0C: return FAMILY_V_IDENTITY;
            default: throw new UnsupportedOperationException("TorCertificateType code:" + code + " unknown");
        }
    }

    public static boolean isX509Certificate(TorCertificateType certType) {
        return X509_CERT_TYPES.contains(certType);
    }

    public static boolean isEdCertificate(TorCertificateType certType) {
        return ED_CERT_TYPES.contains(certType);
    }

    public static boolean isRsaEdCrossCertificate(TorCertificateType certType) {
        return RSA_ED_CROSS_CERT_TYPES.contains(certType);
    }
}
