package com.listaai.infrastructure.adapter.input.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.listaai.BaseIntegrationTest;
import com.listaai.application.service.EmailOutboxWorker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.email-verification.enabled=true",
        "app.email.resend.base-url=http://localhost:9091",
        "app.email.resend.api-key=test-key",
        "app.email.from-address=noreply@test.local"
})
class EmailVerificationIT extends BaseIntegrationTest {

    static WireMockServer wiremock;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmailOutboxWorker worker;

    @BeforeAll
    static void startWiremock() {
        wiremock = new WireMockServer(9091);
        wiremock.start();
    }

    @AfterAll
    static void stopWiremock() { wiremock.stop(); }

    @BeforeEach
    void resetWiremock() { wiremock.resetAll(); }

    @Test
    void full_flow_register_verify_login() {
        wiremock.stubFor(post("/emails").willReturn(okJson("{\"id\":\"r1\"}")));

        // 1. register — expect 202, no tokens
        given().body("{\"email\":\"alice@example.com\",\"password\":\"Secret123!\",\"name\":\"Alice\"}")
                .post("/v1/auth/register")
                .then().statusCode(202);

        // 2. login before verify — 403
        given().body("{\"email\":\"alice@example.com\",\"password\":\"Secret123!\"}")
                .post("/v1/auth/login")
                .then().statusCode(403);

        // 3. run worker → WireMock sees the POST /emails call
        worker.processOutbox();
        wiremock.verify(postRequestedFor(urlEqualTo("/emails"))
                .withRequestBody(matchingJsonPath("$.to[0]", equalTo("alice@example.com"))));

        // 4. read the raw token from outbox payload JSON (store as HASH in DB; raw is in the payload)
        String payload = jdbc.queryForObject(
                "SELECT payload_json FROM email_outbox WHERE recipient = 'alice@example.com' ORDER BY id DESC LIMIT 1",
                String.class);
        String rawToken = payload.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // 5. verify-email — 200
        given().body(Map.of("token", rawToken))
                .post("/v1/auth/verify-email")
                .then().statusCode(200);

        // 6. verify twice — still 200 (idempotent)
        given().body(Map.of("token", rawToken))
                .post("/v1/auth/verify-email")
                .then().statusCode(200);

        // 7. login now — 200 + tokens
        given().body("{\"email\":\"alice@example.com\",\"password\":\"Secret123!\"}")
                .post("/v1/auth/login")
                .then().statusCode(200)
                .body("accessToken", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void resend_respects_cooldown() {
        wiremock.stubFor(post("/emails").willReturn(okJson("{\"id\":\"r1\"}")));

        given().body("{\"email\":\"bob@example.com\",\"password\":\"Secret123!\",\"name\":\"Bob\"}")
                .post("/v1/auth/register").then().statusCode(202);

        given().body(Map.of("email", "bob@example.com"))
                .post("/v1/auth/resend-verification")
                .then().statusCode(429);
    }

    @Test
    void resend_for_unknown_email_returns_200_without_enumeration() {
        given().body(Map.of("email", "ghost@example.com"))
                .post("/v1/auth/resend-verification")
                .then().statusCode(200);
    }

    @Test
    void resend_server_5xx_keeps_row_pending_for_retry() {
        wiremock.stubFor(post("/emails").willReturn(aResponse().withStatus(503)));

        given().body("{\"email\":\"carol@example.com\",\"password\":\"Secret123!\",\"name\":\"Carol\"}")
                .post("/v1/auth/register").then().statusCode(202);

        worker.processOutbox();
        int attempts = jdbc.queryForObject(
                "SELECT attempts FROM email_outbox WHERE recipient = 'carol@example.com'", Integer.class);
        String status = jdbc.queryForObject(
                "SELECT status FROM email_outbox WHERE recipient = 'carol@example.com'", String.class);
        assertThat(attempts).isEqualTo(1);
        assertThat(status).isEqualTo("PENDING");
    }
}
