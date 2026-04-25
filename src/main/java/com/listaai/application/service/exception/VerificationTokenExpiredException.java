package com.listaai.application.service.exception;

public class VerificationTokenExpiredException extends RuntimeException {
    public VerificationTokenExpiredException() { super("Verification token expired"); }
}
