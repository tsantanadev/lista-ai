# Item Endpoint Ownership Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce list ownership on all four item endpoints so only the list owner can read or modify its items.

**Architecture:** `userId` is extracted from the JWT in `ItemListController` and passed as a plain parameter to `ItemListService`. `ItemListServiceImpl` gains a `ListRepository` dependency and guards every method with `existsByIdAndUserId` — throwing `AccessDeniedException` (already mapped to 403) when the caller is not the owner. The persistence layer is unchanged.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security OAuth2 Resource Server (JWT), JUnit 5, Mockito, RestAssured, Testcontainers (PostgreSQL)

---

## Files

| Action | File |
|--------|------|
| Modify | `src/main/java/com/listaai/application/port/input/ItemListService.java` |
| Modify | `src/main/java/com/listaai/application/service/ItemListServiceImpl.java` |
| Modify | `src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java` |
| Modify | `src/test/java/com/listaai/application/service/ItemListServiceImplTest.java` |
| Modify | `src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java` |

---

## Task 1: Add ownership enforcement to `ItemListServiceImpl` (TDD)

**Files:**
- Modify: `src/test/java/com/listaai/application/service/ItemListServiceImplTest.java`
- Modify: `src/main/java/com/listaai/application/port/input/ItemListService.java`
- Modify: `src/main/java/com/listaai/application/service/ItemListServiceImpl.java`

- [ ] **Step 1: Replace `ItemListServiceImplTest` with the updated version**

The new test class adds `@Mock ListRepository listRepository`, stubs ownership for all existing happy-path tests, updates every service call to include `userId`, and adds four new ownership-failure tests.

```java
package com.listaai.application.service;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ItemList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemListServiceImplTest {

    private static final long LIST_ID = 1L;
    private static final long USER_ID = 10L;
    private static final long OTHER_USER_ID = 99L;

    @Mock
    private ItemListRepository repository;

    @Mock
    private ListRepository listRepository;

    @InjectMocks
    private ItemListServiceImpl service;

    // --- getItemsList ---

    @Test
    void getItemsList_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getItemsList(LIST_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getItemsList_returnsMappedItems() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var items = List.of(
                new ItemList(1L, "Milk", false, null, null),
                new ItemList(2L, "Eggs", true, 2.0, "pcs")
        );
        when(repository.getItemsList(LIST_ID)).thenReturn(items);

        List<ItemList> result = service.getItemsList(LIST_ID, USER_ID);

        assertThat(result).hasSize(2).isEqualTo(items);
    }

    @Test
    void getItemsList_returnsEmptyList() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        when(repository.getItemsList(LIST_ID)).thenReturn(List.of());

        List<ItemList> result = service.getItemsList(LIST_ID, USER_ID);

        assertThat(result).isEmpty();
    }

    // --- save ---

    @Test
    void save_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);
        var command = new CreateItemListCommand("Milk", LIST_ID, null, null);

        assertThatThrownBy(() -> service.save(command, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void save_delegatesToRepository_withQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new CreateItemListCommand("Milk", LIST_ID, 1.5, "liters");

        service.save(command, USER_ID);

        verify(repository).save(new ItemList(null, "Milk", false, 1.5, "liters"), LIST_ID);
    }

    @Test
    void save_delegatesToRepository_withNullQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new CreateItemListCommand("Milk", LIST_ID, null, null);

        service.save(command, USER_ID);

        verify(repository).save(new ItemList(null, "Milk", false, null, null), LIST_ID);
    }

    @Test
    void save_returnsNothing() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new CreateItemListCommand("Milk", LIST_ID, null, null);

        assertThatNoException().isThrownBy(() -> service.save(command, USER_ID));
    }

    // --- update ---

    @Test
    void update_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, null, null);

        assertThatThrownBy(() -> service.update(command, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_delegatesToRepository_withQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, 0.5, "kg");
        var updated = new ItemList(3L, "Butter", true, 0.5, "kg");
        when(repository.update(updated, LIST_ID)).thenReturn(updated);

        service.update(command, USER_ID);

        verify(repository).update(new ItemList(3L, "Butter", true, 0.5, "kg"), LIST_ID);
    }

    @Test
    void update_delegatesToRepository_withNullQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, null, null);
        var updated = new ItemList(3L, "Butter", true, null, null);
        when(repository.update(updated, LIST_ID)).thenReturn(updated);

        service.update(command, USER_ID);

        verify(repository).update(new ItemList(3L, "Butter", true, null, null), LIST_ID);
    }

    @Test
    void update_returnsUpdatedDomain() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, 0.5, "kg");
        var updated = new ItemList(3L, "Butter", true, 0.5, "kg");
        when(repository.update(updated, LIST_ID)).thenReturn(updated);

        ItemList result = service.update(command, USER_ID);

        assertThat(result).isEqualTo(updated);
    }

    // --- delete ---

    @Test
    void delete_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(LIST_ID, 2L, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_delegatesToRepository() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);

        service.delete(LIST_ID, 2L, USER_ID);

        verify(repository).delete(LIST_ID, 2L);
    }
}
```

- [ ] **Step 2: Run tests to confirm compile failure**

```bash
./gradlew test --tests "com.listaai.application.service.ItemListServiceImplTest"
```

Expected: **BUILD FAILED** — compilation error because `ItemListService` method signatures don't match yet (missing `userId` parameter).

- [ ] **Step 3: Update `ItemListService` port interface**

```java
package com.listaai.application.port.input;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.domain.model.ItemList;

import java.util.List;

public interface ItemListService {
    List<ItemList> getItemsList(long listId, long userId);
    ItemList save(CreateItemListCommand createCommand, long userId);
    ItemList update(UpdateItemListCommand updateCommand, long userId);
    void delete(long listId, long id, long userId);
}
```

- [ ] **Step 4: Update `ItemListServiceImpl` — inject `ListRepository` and add ownership guard**

```java
package com.listaai.application.service;

import com.listaai.application.port.input.ItemListService;
import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ItemList;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemListServiceImpl implements ItemListService {

    private final ItemListRepository repository;
    private final ListRepository listRepository;

    public ItemListServiceImpl(ItemListRepository repository, ListRepository listRepository) {
        this.repository = repository;
        this.listRepository = listRepository;
    }

    @Override
    public List<ItemList> getItemsList(long listId, long userId) {
        checkOwnership(listId, userId);
        return repository.getItemsList(listId);
    }

    @Override
    public ItemList save(CreateItemListCommand createCommand, long userId) {
        checkOwnership(createCommand.listId(), userId);
        var itemList = new ItemList(null, createCommand.description(), false, createCommand.quantity(), createCommand.uom());
        return repository.save(itemList, createCommand.listId());
    }

    @Override
    public ItemList update(UpdateItemListCommand updateCommand, long userId) {
        checkOwnership(updateCommand.listId(), userId);
        var itemList = new ItemList(updateCommand.id(), updateCommand.description(), updateCommand.checked(), updateCommand.quantity(), updateCommand.uom());
        return repository.update(itemList, updateCommand.listId());
    }

    @Override
    public void delete(long listId, long id, long userId) {
        checkOwnership(listId, userId);
        repository.delete(listId, id);
    }

    private void checkOwnership(long listId, long userId) {
        if (!listRepository.existsByIdAndUserId(listId, userId)) {
            throw new AccessDeniedException("You do not own list: " + listId);
        }
    }
}
```

- [ ] **Step 5: Run unit tests to confirm they pass**

```bash
./gradlew test --tests "com.listaai.application.service.ItemListServiceImplTest"
```

Expected: **BUILD SUCCESSFUL** — all 15 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/listaai/application/port/input/ItemListService.java
git add src/main/java/com/listaai/application/service/ItemListServiceImpl.java
git add src/test/java/com/listaai/application/service/ItemListServiceImplTest.java
git commit -m "feat: enforce list ownership in ItemListServiceImpl (issue #8)"
```

---

## Task 2: Update `ItemListController` to extract `userId` from JWT

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java`

- [ ] **Step 1: Update `ItemListController`**

Add `@AuthenticationPrincipal Jwt jwt` to all four handlers. Extract `userId` and pass it to every service call.

```java
package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.ItemListService;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListPostRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListResponse;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListUpdateRequest;
import com.listaai.infrastructure.adapter.input.rest.mapper.ItemListRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/lists/{listId}/items")
@Tag(name = "Shopping List Items", description = "Add, retrieve, update, and delete items within a shopping list")
@SecurityRequirement(name = "bearerAuth")
public class ItemListController {

    private final ItemListService service;
    private final ItemListRestMapper mapper;

    public ItemListController(ItemListService service, ItemListRestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Get all items in a list",
               description = "Returns all items belonging to the specified shopping list.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Items retrieved successfully",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemListResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "List belongs to a different user",
            content = @Content)
    })
    public ResponseEntity<List<ItemListResponse>> getItemsList(
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId,
            @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        var result = service.getItemsList(listId, userId).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "Add an item to a list",
               description = "Creates a new item in the specified shopping list. The item starts as unchecked.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Item added successfully",
            content = @Content(schema = @Schema(implementation = ItemListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "List belongs to a different user",
            content = @Content)
    })
    public ResponseEntity<ItemListResponse> postItemList(
            @RequestBody ItemListPostRequest request,
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId,
            @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        var command = mapper.toCreateCommand(request, listId);
        var created = service.save(command, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update an item",
               description = "Updates the description and/or checked status of an existing item.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item updated successfully",
            content = @Content(schema = @Schema(implementation = ItemListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "List belongs to a different user",
            content = @Content)
    })
    public ResponseEntity<ItemListResponse> putItemList(
            @RequestBody ItemListUpdateRequest request,
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId,
            @Parameter(description = "ID of the item to update", required = true)
            @PathVariable long itemId,
            @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        var command = mapper.toUpdateCommand(request, itemId, listId);
        var result = service.update(command, userId);
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an item",
               description = "Removes an item from the shopping list.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Item deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "List belongs to a different user",
            content = @Content)
    })
    public ResponseEntity<Void> deleteItemList(
            @Parameter(description = "ID of the item to delete", required = true)
            @PathVariable long id,
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId,
            @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        service.delete(listId, id, userId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
./gradlew build -x test
```

Expected: **BUILD SUCCESSFUL**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java
git commit -m "feat: extract userId from JWT in ItemListController, pass to service (issue #8)"
```

---

## Task 3: Add integration tests for ownership enforcement

**Files:**
- Modify: `src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java`

- [ ] **Step 1: Add cross-user 403 and unauthenticated 401 tests to `ItemListControllerIT`**

Add a private helper and eight new test methods at the end of the class (after `deleteItem_returns204`):

```java
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
```

- [ ] **Step 2: Run integration tests**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT"
```

Expected: **BUILD SUCCESSFUL** — all tests pass, including the 8 new ones.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test
```

Expected: **BUILD SUCCESSFUL** — no regressions.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java
git commit -m "test: verify 403/401 enforcement on item endpoints (issue #8)"
```
