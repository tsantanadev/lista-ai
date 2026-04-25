package com.listaai.application.service.exception;

public class VerificationCooldownException extends RuntimeException {
    public VerificationCooldownException() { super("Please wait before requesting another verification email"); }
}
