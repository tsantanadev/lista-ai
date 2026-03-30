package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ListControllerIT extends BaseIntegrationTest {

    @Test
    void createList_returns201WithBody() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Groceries\"}")
        .when()
            .post("/v1/lists")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Groceries"));
    }

    @Test
    void getAllLists_returnsSeededLists() {
        given().contentType(ContentType.JSON).body("{\"name\":\"List 1\"}").when().post("/v1/lists");
        given().contentType(ContentType.JSON).body("{\"name\":\"List 2\"}").when().post("/v1/lists");

        given()
        .when()
            .get("/v1/lists")
        .then()
            .statusCode(200)
            .body("", hasSize(2));
    }

    @Test
    void getAllLists_returnsEmptyArray() {
        given()
        .when()
            .get("/v1/lists")
        .then()
            .statusCode(200)
            .body("", empty());
    }

    @Test
    void deleteList_returns204() {
        int id = given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"ToDelete\"}")
        .when()
            .post("/v1/lists")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
        .when()
            .delete("/v1/lists/" + id)
        .then()
            .statusCode(204);
    }

    @Test
    void deleteList_nonExistentId_returns204() {
        given()
        .when()
            .delete("/v1/lists/9999")
        .then()
            .statusCode(204);
    }
}
