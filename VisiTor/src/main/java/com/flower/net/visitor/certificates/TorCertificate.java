package com.flower.net.visitor.certificates;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class TorCertificate {
    public static final CertificateFactory X509_CERTIFICATE_FACTORY;
    static {
        try {
            X509_CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public final TorCertificateType certificateType;
    public final byte[] certificateBuffer;
    @Nullable public final X509Certificate certificate;
    @Nullable public final TorEdCertificate edCertificate;
    @Nullable public final TorRsaEdCrossCertificate rsaEdCertificate;

    public TorCertificate(TorCertificateType certificateType, byte[] certificateBuffer) throws CertificateException, NoSuchAlgorithmException, SignatureException {
        this.certificateType = certificateType;
        this.certificateBuffer = certificateBuffer;

        switch(certificateType) {
            case TLS_LINK_X509:
            case RSA_ID_X509:
            case LINK_AUTH_X509:
                final ByteArrayInputStream bis = new ByteArrayInputStream(certificateBuffer);
                this.certificate = (X509Certificate) X509_CERTIFICATE_FACTORY.generateCertificate(bis);

                this.rsaEdCertificate = null;
                this.edCertificate = null;
                break;
            case RSA_ID_V_IDENTITY:
                this.rsaEdCertificate = new TorRsaEdCrossCertificate(certificateBuffer);

                this.certificate = null;
                this.edCertificate = null;
                break;
            case IDENTITY_V_SIGNING:
            case SIGNING_V_TLS_CERT:
            case SIGNING_V_LINK_AUTH:
            case BLINDED_ID_V_SIGNING:
            case HS_IP_V_SIGNING:
            case NTOR_CC_IDENTITY:
            case HS_IP_CC_SIGNING:
            case FAMILY_V_IDENTITY:
                this.edCertificate = new TorEdCertificate(certificateBuffer);

                this.certificate = null;
                this.rsaEdCertificate = null;
                break;
            default: throw new UnsupportedOperationException("CertificateType:" + certificateType + " unknown");
        }
    }

    TorCertificate(TorCertificateType certificateType, X509Certificate certificate) throws CertificateEncodingException {
        assert(TorCertificateType.isX509Certificate(certificateType));
        this.certificateType = certificateType;
        this.certificate = certificate;
        this.certificateBuffer = certificate.getEncoded();;

        this.edCertificate = null;
        this.rsaEdCertificate = null;
    }

    TorCertificate(TorCertificateType certificateType, TorEdCertificate edCertificate) {
        assert(TorCertificateType.isEdCertificate(certificateType));
        this.certificateType = certificateType;
        this.edCertificate = edCertificate;
        this.certificateBuffer = edCertificate.getBuffer();;

        this.certificate = null;
        this.rsaEdCertificate = null;
    }

    TorCertificate(TorCertificateType certificateType, TorRsaEdCrossCertificate rsaEdCertificate) {
        assert(TorCertificateType.isRsaEdCrossCertificate(certificateType));
        this.certificateType = certificateType;
        this.rsaEdCertificate = rsaEdCertificate;
        this.certificateBuffer = rsaEdCertificate.getBuffer();

        this.edCertificate = null;
        this.certificate = null;
    }

    @Override
    public String toString() {
        if (edCertificate != null) return certificateType + "\n" + edCertificate + "\n";
        if (certificate != null) return certificateType + "\n" + certificate.getIssuerX500Principal() + "\n";
        if (rsaEdCertificate != null) return certificateType + "\n" + rsaEdCertificate + "\n";
        throw new RuntimeException("Inner certificate not set.");
    }
}
