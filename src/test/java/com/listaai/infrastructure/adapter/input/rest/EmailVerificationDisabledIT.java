package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.given;

@TestPropertySource(properties = "app.email-verification.enabled=false")
class EmailVerificationDisabledIT extends BaseIntegrationTest {

    @Test
    void register_returns_201_with_tokens_when_flag_off() {
        given().body("{\"email\":\"dave@example.com\",\"password\":\"Secret123!\",\"name\":\"Dave\"}")
                .post("/v1/auth/register")
                .then().statusCode(201)
                .body("accessToken", org.hamcrest.Matchers.notNullValue())
                .body("refreshToken", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void login_succeeds_without_verification_when_flag_off() {
        given().body("{\"email\":\"erin@example.com\",\"password\":\"Secret123!\",\"name\":\"Erin\"}")
                .post("/v1/auth/register").then().statusCode(201);

        given().body("{\"email\":\"erin@example.com\",\"password\":\"Secret123!\"}")
                .post("/v1/auth/login")
                .then().statusCode(200);
    }
}
