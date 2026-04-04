package com.listaai;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE item_list, user_shopping_list, list, oauth_identities, refresh_tokens, users RESTART IDENTITY CASCADE");
    }

    protected String registerAndGetToken(String email, String password, String name) {
        return given()
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"name\":\"" + name + "\"}")
                .when()
                .post("/v1/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");
    }

    protected String defaultUserToken() {
        return registerAndGetToken("test@example.com", "Password123!", "Test User");
    }
}
