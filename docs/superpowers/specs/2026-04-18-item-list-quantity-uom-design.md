# Design: Add quantity and uom to ItemList

**Date:** 2026-04-18
**Status:** Approved

## Overview

Add two optional fields to the `item_list` entity:

- `quantity` — a nullable `Double` representing how much of the item is needed
- `uom` (unit of measure) — a nullable `String` (e.g., "kg", "pcs", "liters")

Both fields are optional on create and updatable via the existing update endpoint.

## Approach

Flat fields added directly to every layer, consistent with how `description` and `checked` are handled today. No value objects or nested API shapes.

## Database

New Liquibase migration: `007_item_list_add_quantity_uom.yaml`

- Adds `quantity DOUBLE PRECISION` (nullable) to `item_list`
- Adds `uom TEXT` (nullable) to `item_list`
- Existing rows unaffected

## Domain Model

`ItemList` record:

```java
record ItemList(Long id, String description, boolean checked, Double quantity, String uom)
```

## Application Layer

Commands updated with two new nullable fields:

```java
record CreateItemListCommand(String description, long listId, Double quantity, String uom)
record UpdateItemListCommand(long id, String description, long listId, boolean checked, Double quantity, String uom)
```

## Persistence Layer

`ItemListEntity` gains `Double quantity` and `String uom` fields with standard getters/setters and `@Column` annotations (nullable).

`ItemListPersistenceMapper` — no changes needed; MapStruct maps by field name automatically.

## REST Layer

DTOs updated:

```java
record ItemListPostRequest(String description, Double quantity, String uom)
record ItemListUpdateRequest(String description, boolean checked, Double quantity, String uom)
record ItemListResponse(long id, String description, boolean checked, Double quantity, String uom)
```

`ItemListRestMapper` — no changes needed; MapStruct maps by field name automatically.

## Files to Change

| File | Change |
|------|--------|
| `src/main/resources/db/changelog/migration/007_item_list_add_quantity_uom.yaml` | New migration |
| `src/main/resources/db/changelog/db.changelog-master.yaml` | Reference new migration |
| `domain/model/ItemList.java` | Add `quantity`, `uom` fields |
| `application/port/input/command/CreateItemListCommand.java` | Add `quantity`, `uom` fields |
| `application/port/input/command/UpdateItemListCommand.java` | Add `quantity`, `uom` fields |
| `infrastructure/.../entity/ItemListEntity.java` | Add `quantity`, `uom` columns |
| `infrastructure/.../dto/ItemListPostRequest.java` | Add `quantity`, `uom` fields |
| `infrastructure/.../dto/ItemListUpdateRequest.java` | Add `quantity`, `uom` fields |
| `infrastructure/.../dto/ItemListResponse.java` | Add `quantity`, `uom` fields |

## Testing

- Existing tests updated to pass `null` for `quantity`/`uom` where records are constructed
- New test cases for create and update with `quantity` and `uom` populated
- New test case for create/update with both fields omitted (null), verifying they remain null
