package com.listaai.application.port.output;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {}
