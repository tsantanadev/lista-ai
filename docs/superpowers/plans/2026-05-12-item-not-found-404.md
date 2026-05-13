# Item Not Found → 404 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `PUT /v1/lists/{listId}/items/{itemId}` and `DELETE /v1/lists/{listId}/items/{itemId}` return 404 when the item does not exist, instead of 500 and 204 respectively.

**Architecture:** Introduce `ItemNotFoundException` following the existing domain exception pattern, throw it from `ItemListPersistenceAdapter.update()` and `delete()`, and map it to 404 in `GlobalExceptionHandler`. Change `ItemListJpaRepository.deleteByIdAndListId` return type from `void` to `int` so the adapter can detect a no-op delete without an extra query.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data JPA, RestAssured (integration tests), AssertJ

---

## File Map

| Action | File |
|--------|------|
| Create | `src/main/java/com/listaai/application/service/exception/ItemNotFoundException.java` |
| Modify | `src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java` |
| Modify | `src/test/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandlerTest.java` |
| Modify | `src/main/java/com/listaai/infrastructure/adapter/output/persistence/ItemListPersistenceAdapter.java` |
| Modify | `src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/ItemListJpaRepository.java` |
| Modify | `src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java` |

---

## Task 1: `ItemNotFoundException` + 404 handler

**Files:**
- Create: `src/main/java/com/listaai/application/service/exception/ItemNotFoundException.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java`
- Test: `src/test/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing test**

Add this import at the top of `GlobalExceptionHandlerTest.java`:
```java
import com.listaai.application.service.exception.ItemNotFoundException;
```

Add this test method to the class body:
```java
@Test
void itemNotFound_mapsTo404() {
    ProblemDetail pd = handler.handleItemNotFound(new ItemNotFoundException(5L, 3L));
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.GlobalExceptionHandlerTest.itemNotFound_mapsTo404"
```

Expected: compilation failure — `ItemNotFoundException` does not exist, `handleItemNotFound` does not exist.

- [ ] **Step 3: Create `ItemNotFoundException`**

Create `src/main/java/com/listaai/application/service/exception/ItemNotFoundException.java`:
```java
package com.listaai.application.service.exception;

public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(long itemId, long listId) {
        super("Item %d not found in list %d".formatted(itemId, listId));
    }
}
```

- [ ] **Step 4: Add handler to `GlobalExceptionHandler`**

Add this import to `GlobalExceptionHandler.java`:
```java
import com.listaai.application.service.exception.ItemNotFoundException;
```

Add this method to the class body (after the existing handlers):
```java
@ExceptionHandler(ItemNotFoundException.class)
public ProblemDetail handleItemNotFound(ItemNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
}
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.GlobalExceptionHandlerTest.itemNotFound_mapsTo404"
```

Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/listaai/application/service/exception/ItemNotFoundException.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java \
        src/test/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandlerTest.java
git commit -m "feat: map ItemNotFoundException to 404 (issue #9)"
```

---

## Task 2: Fix `update()` — 404 on missing item

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/ItemListPersistenceAdapter.java`
- Test: `src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java`

- [ ] **Step 1: Write the failing integration test**

Add this test to `ItemListControllerIT.java` (anywhere in the class body):
```java
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
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT.updateItem_returns404_whenItemNotFound"
```

Expected: FAIL — response is 500, not 404.

- [ ] **Step 3: Update `ItemListPersistenceAdapter.update()`**

In `ItemListPersistenceAdapter.java`, replace the `orElseThrow` line in `update()`:

Old:
```java
.orElseThrow(() -> new IllegalArgumentException(
        "Item %d not found in list %d".formatted(itemList.id(), listId)));
```

New:
```java
.orElseThrow(() -> new ItemNotFoundException(itemList.id(), listId));
```

Add the import at the top of the file:
```java
import com.listaai.application.service.exception.ItemNotFoundException;
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT.updateItem_returns404_whenItemNotFound"
```

Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/output/persistence/ItemListPersistenceAdapter.java \
        src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java
git commit -m "feat: throw ItemNotFoundException in update() when item not found (issue #9)"
```

---

## Task 3: Fix `delete()` — 404 on missing item

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/ItemListJpaRepository.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/ItemListPersistenceAdapter.java`
- Test: `src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java`

- [ ] **Step 1: Write the failing integration test**

Add this test to `ItemListControllerIT.java`:
```java
@Test
void deleteItem_returns404_whenItemNotFound() {
    String token = defaultUserToken();
    int listId = seedList(token);

    given()
        .header("Authorization", "Bearer " + token)
    .when()
        .delete("/v1/lists/" + listId + "/items/99999")
    .then()
        .statusCode(404);
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT.deleteItem_returns404_whenItemNotFound"
```

Expected: FAIL — response is 204, not 404.

- [ ] **Step 3: Change `deleteByIdAndListId` return type to `int`**

In `ItemListJpaRepository.java`, change:
```java
@Transactional
void deleteByIdAndListId(long id, long listId);
```
to:
```java
@Transactional
int deleteByIdAndListId(long id, long listId);
```

- [ ] **Step 4: Update `ItemListPersistenceAdapter.delete()`**

In `ItemListPersistenceAdapter.java`, replace the `delete()` method body:

Old:
```java
@Override
@Transactional
public void delete(long listId, long id) {
    repository.deleteByIdAndListId(id, listId);
}
```

New:
```java
@Override
@Transactional
public void delete(long listId, long id) {
    int deleted = repository.deleteByIdAndListId(id, listId);
    if (deleted == 0) {
        throw new ItemNotFoundException(id, listId);
    }
}
```

(`ItemNotFoundException` import is already present from Task 2.)

- [ ] **Step 5: Run the test to confirm it passes**

```bash
./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT.deleteItem_returns404_whenItemNotFound"
```

Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 6: Run the full test suite to check for regressions**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/ItemListJpaRepository.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/ItemListPersistenceAdapter.java \
        src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java
git commit -m "feat: throw ItemNotFoundException in delete() when item not found (issue #9)"
```
