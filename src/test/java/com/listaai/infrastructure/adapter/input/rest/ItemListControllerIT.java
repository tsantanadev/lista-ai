package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ItemListControllerIT extends BaseIntegrationTest {

    private int seedList(String token) {
        return given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Test List\"}")
        .when()
            .post("/v1/lists")
        .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    private void seedItem(String token, int listId, String description) {
        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"" + description + "\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(201);
    }

    @Test
    void getItems_returnsItemsForList() {
        String token = defaultUserToken();
        int listId = seedList(token);
        seedItem(token, listId, "Milk");
        seedItem(token, listId, "Eggs");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .body("", hasSize(2));
    }

    @Test
    void getItems_emptyList() {
        String token = defaultUserToken();
        int listId = seedList(token);

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .body("", empty());
    }

    @Test
    void createItem_returns201() {
        String token = defaultUserToken();
        int listId = seedList(token);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Milk\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(201);
    }

    @Test
    void updateItem_returns200() {
        String token = defaultUserToken();
        int listId = seedList(token);
        seedItem(token, listId, "Milk");

        int itemId = given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Butter\",\"checked\":true}")
        .when()
            .put("/v1/lists/" + listId + "/items/" + itemId)
        .then()
            .statusCode(200)
            .body("description", equalTo("Butter"))
            .body("checked", equalTo(true));
    }

    @Test
    void deleteItem_returns204() {
        String token = defaultUserToken();
        int listId = seedList(token);
        seedItem(token, listId, "Milk");

        int itemId = given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/v1/lists/" + listId + "/items/" + itemId)
        .then()
            .statusCode(204);
    }
}
