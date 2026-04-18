# Item List: Add quantity and uom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional `quantity` (Double) and `uom` (String) fields to ItemList across all layers of the hexagonal architecture.

**Architecture:** Flat nullable fields are propagated from the database up through the persistence entity, domain model, application commands, and REST DTOs. MapStruct handles all mapping automatically by field name — no mapper changes required.

**Tech Stack:** Java 25, Spring Boot 4.0.0, Liquibase (YAML migrations), MapStruct 1.6.3, JUnit 5, Mockito, REST-assured, PostgreSQL 16.

---

### Task 1: Database migration

**Files:**
- Create: `src/main/resources/db/changelog/migration/007_item_list_add_quantity_uom.yaml`

- [ ] **Step 1: Write the migration file**

```yaml
databaseChangeLog:
  - changeSet:
      id: add-quantity-uom-to-item-list
      author: tsantanadev
      changes:
        - addColumn:
            tableName: item_list
            columns:
              - column:
                  name: quantity
                  type: DOUBLE PRECISION
              - column:
                  name: uom
                  type: TEXT
```

- [ ] **Step 2: Verify Liquibase picks it up**

The master changelog uses `includeAll` on the `migration/` directory, so the new file is auto-included. No change to `db.changelog-master.yaml` needed.

- [ ] **Step 3: Start the app and confirm migration runs cleanly**

```bash
docker-compose up -d
./gradlew bootRun
```

Expected: application starts without Liquibase errors; `item_list` now has `quantity` and `uom` columns.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/migration/007_item_list_add_quantity_uom.yaml
git commit -m "feat: add quantity and uom columns to item_list table"
```

---

### Task 2: Domain model

**Files:**
- Modify: `src/main/java/com/listaai/domain/model/ItemList.java`

- [ ] **Step 1: Update the `ItemList` record**

Replace the file content:

```java
package com.listaai.domain.model;

public record ItemList(Long id, String description, boolean checked, Double quantity, String uom) {}
```

- [ ] **Step 2: Check compilation**

```bash
./gradlew compileJava
```

Expected: compilation fails with errors in `ItemListServiceImpl` and test files that construct `ItemList` with the old 3-argument constructor. This is expected — those are fixed in later tasks.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/listaai/domain/model/ItemList.java
git commit -m "feat: add quantity and uom to ItemList domain record"
```

---

### Task 3: Application commands

**Files:**
- Modify: `src/main/java/com/listaai/application/port/input/command/CreateItemListCommand.java`
- Modify: `src/main/java/com/listaai/application/port/input/command/UpdateItemListCommand.java`

- [ ] **Step 1: Update `CreateItemListCommand`**

```java
package com.listaai.application.port.input.command;

public record CreateItemListCommand(String description, long listId, Double quantity, String uom) {}
```

- [ ] **Step 2: Update `UpdateItemListCommand`**

```java
package com.listaai.application.port.input.command;

public record UpdateItemListCommand(long id, String description, long listId, boolean checked, Double quantity, String uom) {}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/listaai/application/port/input/command/CreateItemListCommand.java \
        src/main/java/com/listaai/application/port/input/command/UpdateItemListCommand.java
git commit -m "feat: add quantity and uom to item list commands"
```

---

### Task 4: Persistence entity

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/ItemListEntity.java`

- [ ] **Step 1: Add fields, constructor args, and accessors**

```java
package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "item_list")
public class ItemListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long listId;

    @Column
    private String description;

    @Column
    private boolean checked;

    @Column
    private Double quantity;

    @Column
    private String uom;

    protected ItemListEntity() {}

    public ItemListEntity(Long id, Long listId, String description, boolean checked, Double quantity, String uom) {
        this.id = id;
        this.listId = listId;
        this.description = description;
        this.checked = checked;
        this.quantity = quantity;
        this.uom = uom;
    }

    public Long getId() { return id; }
    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/ItemListEntity.java
git commit -m "feat: add quantity and uom to ItemListEntity"
```

---

### Task 5: REST DTOs

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ItemListPostRequest.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ItemListUpdateRequest.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ItemListResponse.java`

- [ ] **Step 1: Update `ItemListPostRequest`**

```java
package com.listaai.infrastructure.adapter.input.rest.dto;

public record ItemListPostRequest(String description, Double quantity, String uom) {}
```

- [ ] **Step 2: Update `ItemListUpdateRequest`**

```java
package com.listaai.infrastructure.adapter.input.rest.dto;

public record ItemListUpdateRequest(String description, boolean checked, Double quantity, String uom) {}
```

- [ ] **Step 3: Update `ItemListResponse`**

```java
package com.listaai.infrastructure.adapter.input.rest.dto;

public record ItemListResponse(long id, String description, boolean checked, Double quantity, String uom) {}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ItemListPostRequest.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ItemListUpdateRequest.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ItemListResponse.java
git commit -m "feat: add quantity and uom to item list REST DTOs"
```

---

### Task 6: Service implementation + unit tests

**Files:**
- Modify: `src/main/java/com/listaai/application/service/ItemListServiceImpl.java`
- Modify: `src/test/java/com/listaai/application/service/ItemListServiceImplTest.java`

- [ ] **Step 1: Update the unit tests first**

Replace the full content of `ItemListServiceImplTest.java`:

```java
package com.listaai.application.service;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.domain.model.ItemList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemListServiceImplTest {

    @Mock
    private ItemListRepository repository;

    @InjectMocks
    private ItemListServiceImpl service;

    @Test
    void getItemsList_returnsMappedItems() {
        var items = List.of(
                new ItemList(1L, "Milk", false, null, null),
                new ItemList(2L, "Eggs", true, 2.0, "pcs")
        );
        when(repository.getItemsList(1L)).thenReturn(items);

        List<ItemList> result = service.getItemsList(1L);

        assertThat(result).hasSize(2).isEqualTo(items);
    }

    @Test
    void getItemsList_returnsEmptyList() {
        when(repository.getItemsList(1L)).thenReturn(List.of());

        List<ItemList> result = service.getItemsList(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void save_delegatesToRepository_withQuantityAndUom() {
        var command = new CreateItemListCommand("Milk", 1L, 1.5, "liters");

        service.save(command);

        verify(repository).save(new ItemList(null, "Milk", false, 1.5, "liters"), 1L);
    }

    @Test
    void save_delegatesToRepository_withNullQuantityAndUom() {
        var command = new CreateItemListCommand("Milk", 1L, null, null);

        service.save(command);

        verify(repository).save(new ItemList(null, "Milk", false, null, null), 1L);
    }

    @Test
    void save_returnsNothing() {
        var command = new CreateItemListCommand("Milk", 1L, null, null);

        assertThatNoException().isThrownBy(() -> service.save(command));
    }

    @Test
    void update_delegatesToRepository_withQuantityAndUom() {
        var command = new UpdateItemListCommand(3L, "Butter", 1L, true, 0.5, "kg");
        var updated = new ItemList(3L, "Butter", true, 0.5, "kg");
        when(repository.update(updated, 1L)).thenReturn(updated);

        service.update(command);

        verify(repository).update(new ItemList(3L, "Butter", true, 0.5, "kg"), 1L);
    }

    @Test
    void update_delegatesToRepository_withNullQuantityAndUom() {
        var command = new UpdateItemListCommand(3L, "Butter", 1L, true, null, null);
        var updated = new ItemList(3L, "Butter", true, null, null);
        when(repository.update(updated, 1L)).thenReturn(updated);

        service.update(command);

        verify(repository).update(new ItemList(3L, "Butter", true, null, null), 1L);
    }

    @Test
    void update_returnsUpdatedDomain() {
        var command = new UpdateItemListCommand(3L, "Butter", 1L, true, 0.5, "kg");
        var updated = new ItemList(3L, "Butter", true, 0.5, "kg");
        when(repository.update(updated, 1L)).thenReturn(updated);

        ItemList result = service.update(command);

        assertThat(result).isEqualTo(updated);
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete(1L, 2L);

        verify(repository).delete(1L, 2L);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (compilation errors expected)**

```bash
./gradlew test --tests "com.listaai.application.service.ItemListServiceImplTest"
```

Expected: compilation failure — `ItemListServiceImpl` still uses the old 3-argument `ItemList` constructor.

- [ ] **Step 3: Update `ItemListServiceImpl`**

```java
package com.listaai.application.service;

import com.listaai.application.port.input.ItemListService;
import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.domain.model.ItemList;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemListServiceImpl implements ItemListService {

    private final ItemListRepository repository;

    public ItemListServiceImpl(ItemListRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ItemList> getItemsList(long listId) {
        return repository.getItemsList(listId);
    }

    @Override
    public ItemList save(CreateItemListCommand createCommand) {
        var itemList = new ItemList(null, createCommand.description(), false, createCommand.quantity(), createCommand.uom());
        return repository.save(itemList, createCommand.listId());
    }

    @Override
    public ItemList update(UpdateItemListCommand updateCommand) {
        var itemList = new ItemList(updateCommand.id(), updateCommand.description(), updateCommand.checked(), updateCommand.quantity(), updateCommand.uom());
        return repository.update(itemList, updateCommand.listId());
    }

    @Override
    public void delete(long listId, long id) {
        repository.delete(listId, id);
    }
}
```

- [ ] **Step 4: Run unit tests and confirm they pass**

```bash
./gradlew test --tests "com.listaai.application.service.ItemListServiceImplTest"
```

Expected: BUILD SUCCESSFUL, all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/listaai/application/service/ItemListServiceImpl.java \
        src/test/java/com/listaai/application/service/ItemListServiceImplTest.java
git commit -m "feat: propagate quantity and uom through service layer"
```

---

### Task 7: Integration tests

**Files:**
- Modify: `src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java`

- [ ] **Step 1: Update `seedItem` helper and add new test cases**

Replace the full content of `ItemListControllerIT.java`:

```java
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
}
```

- [ ] **Step 2: Run the full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass including the two new integration tests.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java
git commit -m "test: add integration tests for quantity and uom on item list"
```
