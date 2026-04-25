package com.listaai.application.service.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email address not verified");
    }
}
