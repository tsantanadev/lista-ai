# Item Endpoint Ownership Enforcement

**Date:** 2026-05-12
**Issue:** [#8](https://github.com/tsantanadev/lista-ai/issues/8)
**Status:** Approved, pending implementation

## Problem

`ItemListController` never extracts the caller's identity from the JWT. Any authenticated user who knows a `listId` can read, add, update, or delete items belonging to that list — even if the list belongs to someone else. `ListController` correctly enforces ownership; item endpoints do not.

## Context

- Sharing is a planned future feature. When it arrives, the ownership check will evolve into a membership check (owner or shared member). The design accounts for this migration path.
- For now, ownership and membership are equivalent: you must own the list to operate on its items.

## Design

### Layer responsibilities

```
ItemListController
  ├── add @AuthenticationPrincipal Jwt jwt to all 4 handlers
  ├── extract Long userId = Long.parseLong(jwt.getSubject())
  └── pass userId to ItemListService

ItemListService (port interface)
  └── add long userId to every method:
        getItemsList(long listId, long userId)
        save(CreateItemListCommand, long userId)
        update(UpdateItemListCommand, long userId)
        delete(long listId, long id, long userId)

ItemListServiceImpl
  ├── inject ListRepository (already in Spring context)
  ├── ownership guard at start of every method:
  │     if (!listRepository.existsByIdAndUserId(listId, userId))
  │         throw new AccessDeniedException("You do not own list: " + listId)
  └── all other logic unchanged

ItemListRepository / persistence layer
  └── no changes
```

`userId` is passed as an explicit parameter rather than embedded in command objects, to keep auth identity out of domain commands.

### Future sharing migration path

When sharing is built, `existsByIdAndUserId` becomes `isMemberOfList(listId, userId)` — one call site per method, no other structural changes required.

### Error handling

No new exceptions. `AccessDeniedException` is already thrown by `ListServiceImpl.deleteList` and already mapped to **403 Forbidden** in `GlobalExceptionHandler`. All four item endpoints return 403 with message `"You do not own list: {id}"` when the caller doesn't own the list — consistent with existing list delete behaviour.

Unauthenticated requests continue to return 401 (security config unchanged).

## Testing

### Unit — `ItemListServiceImplTest`

For each of the 4 methods:
- When `listRepository.existsByIdAndUserId` returns `false` → `AccessDeniedException` thrown
- When it returns `true` → delegates to `itemListRepository` as before

`ListRepository` is mocked alongside the existing `ItemListRepository` mock.

### Integration — controller IT test

- Authenticated user can CRUD their own list's items (happy path)
- Authenticated user gets 403 when accessing another user's list items
- Unauthenticated request gets 401

No existing tests are modified — only new cases added.
