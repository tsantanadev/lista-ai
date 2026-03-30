# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run (requires PostgreSQL running)
./gradlew bootRun

# Start PostgreSQL via Docker
docker-compose up -d

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.listaai.YourTestClass"

# Clean build
./gradlew clean build
```

## Architecture

This is a **Spring Boot REST API** for shopping list management, built with **Hexagonal Architecture (Ports & Adapters)**. The package structure enforces strict layering:

```
com.listaai/
├── domain/model/          # Domain entities (ShoppingList, ItemList) — no external deps
├── application/
│   ├── port/input/        # Inbound port interfaces (use cases: ListService, ItemListService)
│   │   └── command/       # Input command objects (CreateListCommand, etc.)
│   ├── port/output/       # Outbound port interfaces (repository contracts)
│   └── service/           # Use case implementations (ListServiceImpl, ItemListServiceImpl)
└── infrastructure/
    └── adapter/
        ├── input/rest/    # REST controllers, DTOs, MapStruct REST mappers
        └── output/persistence/  # JPA adapters, JPA entities, MapStruct persistence mappers, Spring Data repos
```

**Data flow:** REST controller → command object → service (application port) → persistence adapter (output port) → Spring Data JPA → PostgreSQL.

**Key conventions:**
- Domain model and JPA entities are separate classes; MapStruct mappers convert between them at the persistence boundary (e.g., [ListPersistenceMapper](src/main/java/com/listaai/infrastructure/adapter/output/persistence/mapper/ListPersistenceMapper.java))
- REST DTOs are also separate from domain; mapped via REST mappers (e.g., [ItemListRestMapper](src/main/java/com/listaai/infrastructure/adapter/input/rest/mapper/ItemListRestMapper.java))
- Services depend only on port interfaces, never on infrastructure classes directly

## Tech Stack

- **Java 25** + **Spring Boot 4.0.0**
- **PostgreSQL 16** (Docker Compose in [compose.yaml](compose.yaml))
- **Liquibase** for schema migrations (YAML changelogs under `src/main/resources/db/changelog/migration/`)
- **Hibernate** in `validate` mode — schema must exist before app starts
- **MapStruct 1.6.3** via `annotationProcessor` for compile-time mapper generation (Spring component model)

## Database

Liquibase manages the schema. Add new migrations as numbered YAML files in `src/main/resources/db/changelog/migration/` and reference them in [db.changelog-master.yaml](src/main/resources/db/changelog/db.changelog-master.yaml).

Current schema: `list` (id, name) → `item_list` (id, list_id FK, description, checked).

Connection defaults (from [application.yaml](src/main/resources/application.yaml)):
- URL: `jdbc:postgresql://localhost:5432/lista_ai_db`
- User/password: `postgres` / `password`
