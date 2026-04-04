package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class SwaggerIT extends BaseIntegrationTest {

    @Test
    void openApiSpecIsAccessibleWithoutAuthentication() {
        given()
            .accept("application/json")
            .when()
            .get("/v3/api-docs")
            .then()
            .statusCode(200)
            .body("openapi", notNullValue())
            .body("info.title", equalTo("Lista AI API"));
    }

    @Test
    void swaggerUiIsAccessibleWithoutAuthentication() {
        given()
            .when()
            .get("/swagger-ui/index.html")
            .then()
            .statusCode(200);
    }
}
