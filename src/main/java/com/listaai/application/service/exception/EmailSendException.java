package com.listaai.application.service.exception;

public class EmailSendException extends RuntimeException {
    private final boolean retryable;

    public EmailSendException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmailSendException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
