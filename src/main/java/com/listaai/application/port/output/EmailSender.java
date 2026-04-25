package com.listaai.application.port.output;

import com.listaai.application.service.exception.EmailSendException;

public interface EmailSender {
    /**
     * Sends a fully-rendered message. Vendor concepts (API keys, template IDs)
     * must not appear in this interface.
     *
     * @throws EmailSendException on any failure. The exception's {@code retryable}
     *         flag tells callers whether to schedule a retry.
     */
    void send(EmailMessage message);
}
