package com.flower.crypt;

import java.security.cert.X509Certificate;

public class CertificateVerifier {
    public static void main(String[] args) {
        X509Certificate cer1 = PkiUtil.getCertificateFromResource("socks5s_server.crt");
        X509Certificate caCer = PkiUtil.getCertificateFromResource("server_CA.crt");

        boolean verificationSuccess = PkiUtil.verifyCertificateSignature(cer1, caCer);
        System.out.println(verificationSuccess ? "Verification success" : "Verification failure");
    }
}
