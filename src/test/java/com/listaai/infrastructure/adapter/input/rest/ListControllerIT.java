package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ListControllerIT extends BaseIntegrationTest {

    @Test
    void createList_returns201WithBody() {
        String token = defaultUserToken();

        given()
            .header("Authorization", "Bearer " + token)
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
        String token = defaultUserToken();

        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body("{\"name\":\"List 1\"}").when().post("/v1/lists");
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body("{\"name\":\"List 2\"}").when().post("/v1/lists");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists")
        .then()
            .statusCode(200)
            .body("", hasSize(2));
    }

    @Test
    void getAllLists_returnsEmptyArray() {
        String token = defaultUserToken();

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists")
        .then()
            .statusCode(200)
            .body("", empty());
    }

    @Test
    void deleteList_returns204() {
        String token = defaultUserToken();

        int id = given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"ToDelete\"}")
        .when()
            .post("/v1/lists")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/v1/lists/" + id)
        .then()
            .statusCode(204);
    }

    @Test
    void deleteList_nonExistentId_returns403() {
        String token = defaultUserToken();

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/v1/lists/9999")
        .then()
            .statusCode(403);
    }
}
