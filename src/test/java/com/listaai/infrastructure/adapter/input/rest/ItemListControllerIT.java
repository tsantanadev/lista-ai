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
    void createItem_withQuantityAndUom_returnsCreatedBody() {
        String token = defaultUserToken();
        int listId = seedList(token);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Milk\",\"quantity\":2.0,\"uom\":\"liters\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(201)
            .body("description", equalTo("Milk"))
            .body("quantity", equalTo(2.0f))
            .body("uom", equalTo("liters"));
    }

    @Test
    void createItem_withoutQuantityAndUom_returnsNullFields() {
        String token = defaultUserToken();
        int listId = seedList(token);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Milk\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(201)
            .body("quantity", nullValue())
            .body("uom", nullValue());
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
    void updateItem_withQuantityAndUom_returnsUpdatedBody() {
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
            .body("{\"description\":\"Milk\",\"checked\":false,\"quantity\":1.5,\"uom\":\"kg\"}")
        .when()
            .put("/v1/lists/" + listId + "/items/" + itemId)
        .then()
            .statusCode(200)
            .body("quantity", equalTo(1.5f))
            .body("uom", equalTo("kg"));
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

    private int getFirstItemId(String token, int listId) {
        return given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(200)
            .extract()
            .path("[0].id");
    }

    // --- cross-user 403 ---

    @Test
    void getItems_returns403_whenNotOwner() {
        String ownerToken = defaultUserToken();
        String otherToken = registerAndGetToken("other@example.com", "Password123!", "Other");
        int listId = seedList(ownerToken);

        given()
            .header("Authorization", "Bearer " + otherToken)
        .when()
            .get("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(403);
    }

    @Test
    void createItem_returns403_whenNotOwner() {
        String ownerToken = defaultUserToken();
        String otherToken = registerAndGetToken("other@example.com", "Password123!", "Other");
        int listId = seedList(ownerToken);

        given()
            .header("Authorization", "Bearer " + otherToken)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Milk\"}")
        .when()
            .post("/v1/lists/" + listId + "/items")
        .then()
            .statusCode(403);
    }

    @Test
    void updateItem_returns403_whenNotOwner() {
        String ownerToken = defaultUserToken();
        String otherToken = registerAndGetToken("other@example.com", "Password123!", "Other");
        int listId = seedList(ownerToken);
        seedItem(ownerToken, listId, "Milk");
        int itemId = getFirstItemId(ownerToken, listId);

        given()
            .header("Authorization", "Bearer " + otherToken)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Butter\",\"checked\":true}")
        .when()
            .put("/v1/lists/" + listId + "/items/" + itemId)
        .then()
            .statusCode(403);
    }

    @Test
    void deleteItem_returns403_whenNotOwner() {
        String ownerToken = defaultUserToken();
        String otherToken = registerAndGetToken("other@example.com", "Password123!", "Other");
        int listId = seedList(ownerToken);
        seedItem(ownerToken, listId, "Milk");
        int itemId = getFirstItemId(ownerToken, listId);

        given()
            .header("Authorization", "Bearer " + otherToken)
        .when()
            .delete("/v1/lists/" + listId + "/items/" + itemId)
        .then()
            .statusCode(403);
    }

    // --- unauthenticated 401 ---

    @Test
    void getItems_returns401_whenUnauthenticated() {
        given()
        .when()
            .get("/v1/lists/1/items")
        .then()
            .statusCode(401);
    }

    @Test
    void createItem_returns401_whenUnauthenticated() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Milk\"}")
        .when()
            .post("/v1/lists/1/items")
        .then()
            .statusCode(401);
    }

    @Test
    void updateItem_returns401_whenUnauthenticated() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Butter\",\"checked\":true}")
        .when()
            .put("/v1/lists/1/items/1")
        .then()
            .statusCode(401);
    }

    @Test
    void deleteItem_returns401_whenUnauthenticated() {
        given()
        .when()
            .delete("/v1/lists/1/items/1")
        .then()
            .statusCode(401);
    }

    @Test
    void updateItem_returns404_whenItemNotFound() {
        String token = defaultUserToken();
        int listId = seedList(token);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"description\":\"Butter\",\"checked\":true}")
        .when()
            .put("/v1/lists/" + listId + "/items/99999")
        .then()
            .statusCode(404);
    }
}
