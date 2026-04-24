package com.listaai.application.service.exception;

public class VerificationTokenSupersededException extends RuntimeException {
    public VerificationTokenSupersededException() {
        super("Verification token superseded by a newer one");
    }
}
