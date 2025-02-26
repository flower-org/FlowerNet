package com.flower.crypt;

import javax.net.ssl.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.net.Socket;

public class SSLCertificateExtractor {
    public static void main(String[] args) {
        String host = "1.1.1.1"; // Change to your server
        int port = 853; // Change to your port

        try (Socket socket = SSLSocketFactory.getDefault().createSocket(host, port)) {
            SSLSession session = ((SSLSocket) socket).getSession();
            Certificate[] certs = session.getPeerCertificates();

            if (certs.length > 0 && certs[0] instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) certs[0];

                System.out.println("-----BEGIN CERTIFICATE-----");
                System.out.println(java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                            .encodeToString(x509Cert.getEncoded()));
                System.out.println("-----END CERTIFICATE-----");
            } else {
                System.err.println("No certificate found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
