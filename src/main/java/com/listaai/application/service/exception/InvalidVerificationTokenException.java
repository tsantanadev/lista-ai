package com.listaai.application.service.exception;

public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException() { super("Invalid verification token"); }
}
