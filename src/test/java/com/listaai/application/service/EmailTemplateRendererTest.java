package com.listaai.application.service;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.infrastructure.config.EmailVerificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties props = new EmailVerificationProperties(
                true, 24, 60, "https://app.test/verify-email");
        renderer = new EmailTemplateRenderer(props);
    }

    @Test
    void renders_verify_email_template() {
        OutboxRow row = new OutboxRow(
                1L, "VERIFY_EMAIL", "alice@example.com",
                "{\"token\":\"ABC123\",\"name\":\"Alice\"}",
                0);

        EmailMessage msg = renderer.render(row);

        assertThat(msg.to()).isEqualTo("alice@example.com");
        assertThat(msg.subject()).contains("verify");
        assertThat(msg.htmlBody()).contains("https://app.test/verify-email?token=ABC123");
        assertThat(msg.htmlBody()).contains("Alice");
        assertThat(msg.textBody()).contains("https://app.test/verify-email?token=ABC123");
    }

    @Test
    void throws_on_unknown_template() {
        OutboxRow row = new OutboxRow(1L, "UNKNOWN", "a@b", "{}", 0);
        assertThatThrownBy(() -> renderer.render(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
