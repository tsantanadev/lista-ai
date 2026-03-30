package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ItemListControllerIT extends BaseIntegrationTest {

    private int seedList() {
        return given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Test List\"}")
        .when()
            .post("/v1/lists")
        .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    private void seedItem(int listId, String description) {
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\":\"" + description + "\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(201);
    }

    @Test
    void getItems_returnsItemsForList() {
        int listId = seedList();
        seedItem(listId, "Milk");
        seedItem(listId, "Eggs");

        given()
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .body("", hasSize(2));
    }

    @Test
    void getItems_emptyList() {
        int listId = seedList();

        given()
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .body("", empty());
    }

    @Test
    void createItem_returns201() {
        int listId = seedList();

        given()
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Milk\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(201);
    }

    @Test
    void updateItem_returns200() {
        int listId = seedList();
        seedItem(listId, "Milk");

        int itemId = given()
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        given()
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
        int listId = seedList();
        seedItem(listId, "Milk");

        int itemId = given()
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        given()
        .when()
            .delete("/v1/lists/" + listId + "/items/" + itemId)
        .then()
            .statusCode(204);
    }
}
