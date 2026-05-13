# Design: Item Not Found → 404 (issue #9)

## Problem

`ItemListPersistenceAdapter.update()` throws `IllegalArgumentException` when the requested item doesn't exist. `GlobalExceptionHandler` has no handler for `IllegalArgumentException`, so Spring Boot falls back to 500. `delete()` silently does nothing for a missing item, returning 204 instead of 404.

## Goal

`PUT /v1/lists/{listId}/items/{itemId}` and `DELETE /v1/lists/{listId}/items/{itemId}` return **404 Not Found** with a ProblemDetail body when the item does not exist.

## Design

### 1. `ItemNotFoundException`

New class: `com.listaai.application.service.exception.ItemNotFoundException`

```java
public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(long itemId, long listId) {
        super("Item %d not found in list %d".formatted(itemId, listId));
    }
}
```

Follows the same pattern as all other domain exceptions in this package.

### 2. `ItemListJpaRepository`

Change `deleteByIdAndListId` return type from `void` to `int`. Spring Data JPA derived delete methods support `int` (number of deleted rows), allowing detection of a no-op delete without a separate query.

```java
@Transactional
int deleteByIdAndListId(long id, long listId);
```

### 3. `ItemListPersistenceAdapter`

**`update()`** — replace `IllegalArgumentException` with `ItemNotFoundException`:

```java
var entity = repository.findByIdAndListId(itemList.id(), listId)
        .orElseThrow(() -> new ItemNotFoundException(itemList.id(), listId));
```

**`delete()`** — check deleted row count, throw if zero:

```java
public void delete(long listId, long id) {
    int deleted = repository.deleteByIdAndListId(id, listId);
    if (deleted == 0) {
        throw new ItemNotFoundException(id, listId);
    }
}
```

### 4. `GlobalExceptionHandler`

Add one handler, consistent with all existing handlers:

```java
@ExceptionHandler(ItemNotFoundException.class)
public ProblemDetail handleItemNotFound(ItemNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
}
```

## Data Flow

```
PUT /update or DELETE /delete (missing item)
  → ItemListPersistenceAdapter detects absence
  → throws ItemNotFoundException
  → GlobalExceptionHandler.handleItemNotFound()
  → 404 ProblemDetail { status: 404, detail: "Item 5 not found in list 3" }
```

## Error Handling

The message in `ItemNotFoundException` is client-safe. Existing `IllegalArgumentException` throws in JWT processing, auth provider resolution, and email template rendering are unaffected — they remain unmapped and produce 500 as appropriate for programming errors.

## Tests

| Test file | New test |
|---|---|
| `GlobalExceptionHandlerTest` | `itemNotFound_mapsTo404()` — unit test, direct handler invocation |
| `ItemListControllerIT` | `updateItem_returns404_whenItemNotFound()` — integration test, PUT with bogus item ID |
| `ItemListControllerIT` | `deleteItem_returns404_whenItemNotFound()` — integration test, DELETE with bogus item ID |

## Files Changed

| File | Change |
|---|---|
| `application/service/exception/ItemNotFoundException.java` | New |
| `infrastructure/adapter/output/persistence/repository/ItemListJpaRepository.java` | `void` → `int` on `deleteByIdAndListId` |
| `infrastructure/adapter/output/persistence/ItemListPersistenceAdapter.java` | `update()` and `delete()` throw `ItemNotFoundException` |
| `infrastructure/adapter/input/rest/GlobalExceptionHandler.java` | Add `handleItemNotFound` handler |
| `infrastructure/adapter/input/rest/GlobalExceptionHandlerTest.java` | Add `itemNotFound_mapsTo404` test |
| `infrastructure/adapter/input/rest/ItemListControllerIT.java` | Add two 404 integration tests |
