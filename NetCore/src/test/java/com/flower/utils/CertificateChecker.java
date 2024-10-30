package com.flower.utils;

import java.security.cert.X509Certificate;

public class CertificateChecker {
    public static void main(String[] args) {
        X509Certificate cer1 = PkiUtil.getCertificateFromResource("MY_REQ.crt");
        X509Certificate caCer = PkiUtil.getCertificateFromResource("pkcs11_ca.crt");

        boolean verificationSuccess = PkiUtil.verifyCertificateSignature(cer1, caCer);
        System.out.println(verificationSuccess ? "Verification success" : "Verification failure");
    }
}
