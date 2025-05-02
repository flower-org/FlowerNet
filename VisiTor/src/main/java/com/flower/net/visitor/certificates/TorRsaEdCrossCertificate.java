package com.flower.net.visitor.certificates;

import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Date;

// TODO: Signature verification
public class TorRsaEdCrossCertificate {
    public final byte[] certificateBuffer;

    /** The subject key (32 bytes) */
    public final byte[] ed25519Key;
    /** RSA Signature */
    public final byte[] rsSignature;

    /** When the cert becomes invalid */
    public final long rawExpirationDate;
    public final Date expirationDate;

    public TorRsaEdCrossCertificate(byte[] certificateBuffer) throws NoSuchAlgorithmException, SignatureException {
        this.certificateBuffer = certificateBuffer;

        if (certificateBuffer.length < 37) {
            throw new RuntimeException("Certificate Buffer too small");
        }
        ed25519Key = new byte[32];
        byte[] expirationDateArray = new byte[4];

        System.arraycopy(certificateBuffer, 0, ed25519Key, 0, 32);
        System.arraycopy(certificateBuffer, 32, expirationDateArray, 0, 4);
        // looks like we want BigEndian
        rawExpirationDate = TorUtils.toUInt32BigEndian(expirationDateArray);
        long expirationInMillis = rawExpirationDate * 3600 * 1000;
        expirationDate = new Date(expirationInMillis);

        int signlen = certificateBuffer[36] & 0xFF;
        rsSignature = new byte[signlen];
        System.arraycopy(certificateBuffer, 37, rsSignature, 0, signlen);

        if (37 + signlen != certificateBuffer.length) {
            throw new RuntimeException("Size mismatch");
        }

/*        byte[] prefix = "Tor TLS RSA/Ed25519 cross-certificate".getBytes();
        byte[] signDataBytes = new byte[prefix.length + 36];
        System.arraycopy(prefix, 0, signDataBytes, 0, prefix.length);
        System.arraycopy(certificateBuffer, 0, signDataBytes, prefix.length, 36);*/

        // TODO: unclear how to VERIFY the signature!!!! Where's the public key?
        // Verify the signature
        /*Signature verifier = Signature.getInstance("SHA256withRSA/PKCS1Padding");
        verifier.initVerify(publicKey);
        verifier.update(signDataBytes);
        boolean isVerified = verifier.verify(rsSignature);
        System.out.println(isVerified);*/
    }

    public byte[] getBuffer() {
        return certificateBuffer;
    }

    @Override
    public String toString() {
        return "TorRsaEdCrossCertificate{" +
                "\nexpirationDate=" + expirationDate +
                ", \ned25519Key=" + TorUtils.bytesToHex(ed25519Key) +
                ", \nrsSignature=" + TorUtils.bytesToHex(rsSignature) +
                ", \nrawExpirationDate=" + rawExpirationDate +
                "}\n";
    }
}
