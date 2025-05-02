package com.flower.net.visitor.certificates;

public class TorCertificateValidationException extends Exception {
    public TorCertificateValidationException(String message) {
        super(message);
    }

    public TorCertificateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
