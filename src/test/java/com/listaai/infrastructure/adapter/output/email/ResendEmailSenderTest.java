package com.listaai.infrastructure.adapter.output.email;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.service.exception.EmailSendException;
import com.listaai.infrastructure.config.EmailProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailSenderTest {

    private WireMockServer wm;
    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        EmailProperties props = new EmailProperties(
                "noreply@test.local", "resend",
                new EmailProperties.Resend("key-xyz", "http://localhost:" + wm.port()),
                new EmailProperties.Worker(10000));
        sender = new ResendEmailSender(props);
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void posts_to_resend_and_succeeds() {
        wm.stubFor(post("/emails")
                .willReturn(okJson("{\"id\":\"abc\"}")));

        sender.send(new EmailMessage("u@x.com", "sub", "<p>h</p>", "t"));

        wm.verify(postRequestedFor(urlEqualTo("/emails"))
                .withHeader("Authorization", equalTo("Bearer key-xyz"))
                .withRequestBody(matchingJsonPath("$.to[0]", equalTo("u@x.com")))
                .withRequestBody(matchingJsonPath("$.subject", equalTo("sub")))
                .withRequestBody(matchingJsonPath("$.html", equalTo("<p>h</p>")))
                .withRequestBody(matchingJsonPath("$.text", equalTo("t")))
                .withRequestBody(matchingJsonPath("$.from", equalTo("noreply@test.local"))));
    }

    @Test
    void wraps_4xx_as_non_retryable() {
        wm.stubFor(post("/emails").willReturn(aResponse().withStatus(400).withBody("bad")));

        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> !((EmailSendException) e).isRetryable());
    }

    @Test
    void wraps_429_as_retryable() {
        wm.stubFor(post("/emails").willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> ((EmailSendException) e).isRetryable());
    }

    @Test
    void wraps_5xx_as_retryable() {
        wm.stubFor(post("/emails").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> ((EmailSendException) e).isRetryable());
    }

    @Test
    void wraps_connection_error_as_retryable() {
        wm.stop();
        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> ((EmailSendException) e).isRetryable());
    }
}
