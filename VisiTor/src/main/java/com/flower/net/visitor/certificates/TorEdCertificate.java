package com.flower.net.visitor.certificates;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// TODO: Signature verification
public class TorEdCertificate {
    /** The version of this format */
    public static final int VERSION = 1;

    public final byte[] certificateBuffer;

    /** Purpose and meaning of the cert */
    public final TorCertificateType certType;
    /** When the cert becomes invalid */
    public final long rawExpirationDate;
    public final Date expirationDate;
    /** Type of CERTIFIED_KEY */
    public final TorCertificateKeyType certKeyType;
    /** Certified key, or its digest */
    public final byte[] certifiedKey;
    public final byte[] signature;

    List<TorEdCertificateExtension> extensions = new ArrayList<>();

    public TorEdCertificate(byte[] certificateBuffer) {
        this.certificateBuffer = certificateBuffer;

        if (certificateBuffer.length < 104) {
            throw new RuntimeException("Certificate Buffer too small");
        }

        int version = certificateBuffer[0] & 0xFF;
        if (version != VERSION) {
            throw new RuntimeException("Ed25519 structure version mismatch");
        }

        int certTypeCode = certificateBuffer[1] & 0xFF;
        certType = TorCertificateType.fromCode(certTypeCode);

        byte[] expirationDateArray = new byte[4];
        System.arraycopy(certificateBuffer, 2, expirationDateArray, 0, 4);
        // looks like we want BigEndian
        rawExpirationDate = TorUtils.toUInt32BigEndian(expirationDateArray);
        long expirationInMillis = rawExpirationDate * 3600 * 1000;
        expirationDate = new Date(expirationInMillis);

        int certKeyTypeCode = certificateBuffer[6] & 0xFF;
        certKeyType = TorCertificateKeyType.fromCode(certKeyTypeCode);

        certifiedKey = new byte[32];
        System.arraycopy(certificateBuffer, 7, certifiedKey, 0, 32);

        int extensionCount = certificateBuffer[39] & 0xFF;
        int offset = 40;
        for (int i = 0; i < extensionCount; i++) {
            // Length of encoded extension body
            int extLen = TorUtils.toUInt16BigEndian(certificateBuffer, offset);
            int extTypeCode = certificateBuffer[offset + 2] & 0xFF;
            TorEdCertificateExtensionType extType = TorEdCertificateExtensionType.fromCode(extTypeCode);
            byte extFlags = certificateBuffer[offset + 3];
            byte[] extData = new byte[extLen];
            System.arraycopy(certificateBuffer, offset + 4, extData, 0, extLen);

            TorEdCertificateExtension extension = new TorEdCertificateExtension(extType, extFlags, extData);
            extensions.add(extension);

            offset += 4 + extLen;
        }

        signature = new byte[64];
        System.arraycopy(certificateBuffer, offset, signature, 0, 64);

        if (offset + 64 != certificateBuffer.length) {
            throw new RuntimeException("Size mismatch");
        }
    }

    public byte[] getBuffer() {
        return certificateBuffer;
    }

    @Override
    public String toString() {
        return "TorEdCertificate{" +
                "\nexpirationDate=" + expirationDate +
                ", \ncertType=" + certType +
                ", \ncertKeyType=" + certKeyType +
                ", \ncertifiedKey=" + TorUtils.bytesToHex(certifiedKey) +
                ", \nsignature=" + TorUtils.bytesToHex(signature) +
                ", \nrawExpirationDate=" + rawExpirationDate +
                ", \nextensions=" + extensions +
                "}\n";
    }
}
