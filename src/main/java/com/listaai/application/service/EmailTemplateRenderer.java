package com.listaai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.infrastructure.config.EmailVerificationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EmailTemplateRenderer {

    public static final String TEMPLATE_VERIFY_EMAIL = "VERIFY_EMAIL";

    private final EmailVerificationProperties verifyProps;
    private final ObjectMapper mapper = new ObjectMapper();

    public EmailTemplateRenderer(EmailVerificationProperties verifyProps) {
        this.verifyProps = verifyProps;
    }

    public EmailMessage render(OutboxRow row) {
        return switch (row.template()) {
            case TEMPLATE_VERIFY_EMAIL -> renderVerifyEmail(row);
            default -> throw new IllegalArgumentException("Unknown template: " + row.template());
        };
    }

    private EmailMessage renderVerifyEmail(OutboxRow row) {
        JsonNode payload = parse(row.payloadJson());
        String token = payload.path("token").asText();
        String name = payload.path("name").asText("");
        String verifyUrl = verifyProps.redirectBaseUrl() + "?token=" + token;

        String subject = "Please verify your email";
        String html = """
                <p>Hi %s,</p>
                <p>Please verify your email address by clicking the link below:</p>
                <p><a href="%s">Verify my email</a></p>
                <p>This link expires in %d hours.</p>
                """.formatted(escape(name), verifyUrl, verifyProps.tokenTtlHours());
        String text = """
                Hi %s,

                Please verify your email by visiting:
                %s

                This link expires in %d hours.
                """.formatted(name, verifyUrl, verifyProps.tokenTtlHours());

        return new EmailMessage(row.recipient(), subject, html, text);
    }

    private JsonNode parse(String json) {
        try { return mapper.readTree(json); }
        catch (IOException e) { throw new IllegalStateException("Malformed outbox payload", e); }
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
