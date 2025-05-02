package com.flower.net.visitor.certificates;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.util.Date;
import java.util.List;

/**
 * From https://spec.torproject.org/tor-spec/negotiating-channels.html#auth-responder
 * <p>
 * - The CERTS cell contains exactly one CertType 4 Ed25519 IDENTITY_V_SIGNING_CERT.
 * This cert must be self-signed; the signing key must be included in a “signed-with-ed25519-key” extension.
 * This signing key is KP_relayid_ed. The subject key is KP_relaysign_ed.
 * <p>
 * - The CERTS cell contains exactly one CertType 5 Ed25519 SIGNING_V_TLS_CERT certificate.
 * This cert must be signed with KP_relaysign_ed. Its subject must be the SHA-256 digest of the TLS certificate
 * that was presented during the TLS handshake.
 * <p>
 * - All of the certs above must be correctly signed, and not expired.
 * <p>
 * The initiator must check all of the above. If this is successful the initiator knows that the responder has
 * the identity KP_relayid_ed.
 * <p>
 * https://spec.torproject.org/cert-spec.html#ed-certs
 * - The ExtFlags field holds flags. Only one flag is currently defined:
 * 1: AFFECTS_VALIDATION. If this flag is present, then the extension affects whether the certificate is valid; ??? WAT
 * implementations MUST NOT accept the certificate as valid unless they recognize the ExtType and accept the extension as valid. ???
 */
public class CertificatesValidator {
    // TODO: from https://spec.torproject.org/tor-spec/relay-keys.html#identity
    //  verify identity key KP_relayid_ed, KS_relayid_ed: An “ed25519 identity key”, also sometimes called a “master identity key”.
    //  This is an Ed25519 key. This key never expires. It is used for only one purpose: signing the KP_relaysign_ed key,
    //  which is used to sign other important keys and objects.
    public static void verifyCertificates(List<TorCertificate> certList) throws TorCertificateValidationException {
        TorCertificate identityCertificateTor = null;
        TorCertificate signingCertificateTor = null;

        for (TorCertificate certificate : certList) {
            if (certificate.certificateType == TorCertificateType.IDENTITY_V_SIGNING) {
                identityCertificateTor = certificate;
            }
            if (certificate.certificateType == TorCertificateType.SIGNING_V_TLS_CERT) {
                signingCertificateTor = certificate;
            }
        }

        if (identityCertificateTor == null) {
            throw new TorCertificateValidationException("Identity certificate not found");
        }
        if (signingCertificateTor == null) {
            throw new TorCertificateValidationException("Signing certificate not found");
        }
        TorEdCertificate identityCertificate = identityCertificateTor.edCertificate;
        TorEdCertificate signingCertificate = signingCertificateTor.edCertificate;
        if (identityCertificate == null) {
            throw new TorCertificateValidationException("Identity certificate is not Ed25519 certificate");
        }
        if (signingCertificate == null) {
            throw new TorCertificateValidationException("Signing certificate is not Ed25519 certificate");
        }
        TorEdCertificateExtension signedWithEd25519KeyExtension = identityCertificate.extensions.isEmpty() ? null : identityCertificate.extensions.get(0);
        if (signedWithEd25519KeyExtension == null) {
            throw new TorCertificateValidationException("Identity certificate doesn't have `signed-with-ed25519-key` extension");
        }

        Ed25519PublicKeyParameters KP_relayid_ed = new Ed25519PublicKeyParameters(signedWithEd25519KeyExtension.extData, 0);
        boolean identityCertificateValid = validateEdCertSignature(KP_relayid_ed, identityCertificate);

        Ed25519PublicKeyParameters KP_relaysign_ed = new Ed25519PublicKeyParameters(identityCertificate.certifiedKey, 0);
        boolean signingCertificateValid = validateEdCertSignature(KP_relaysign_ed, signingCertificate);

        //TODO: signingCertificate subject must be the SHA-256 digest of the TLS certificate that was presented during the TLS handshake.
        System.out.println(TorUtils.bytesToHex(signingCertificate.certifiedKey));

        if (!identityCertificateValid) {
            throw new TorCertificateValidationException("Identity certificate invalid");
        }
        if (!signingCertificateValid) {
            throw new TorCertificateValidationException("Signing certificate invalid");
        }

        Date currentDate = new Date();
        if (identityCertificate.expirationDate.before(currentDate)) {
            throw new TorCertificateValidationException("Identity certificate expired");
        }
        if (signingCertificate.expirationDate.before(currentDate)) {
            throw new TorCertificateValidationException("Signing certificate expired");
        }
    }

    public static boolean validateEdCertSignature(Ed25519PublicKeyParameters publicKey, TorEdCertificate edCertificate) {
        /*MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] fingerprint = digest.digest(publicKey.getEncoded());

        // Convert fingerprint to hex string for readability
        String fingerprintHex = TorUtils.bytesToHex(fingerprint);
        System.out.println("Fingerprint: " + fingerprintHex);*/


        // Initialize the signer
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(false, publicKey); // false for verification

        byte[] message = new byte[edCertificate.certificateBuffer.length - 64];
        System.arraycopy(edCertificate.certificateBuffer, 0, message, 0, message.length);

        // Update the signer with the message
        signer.update(message, 0, message.length);

        // Verify the signature
        return signer.verifySignature(edCertificate.signature);
    }
}
