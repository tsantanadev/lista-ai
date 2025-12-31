# 🛒 Lista-AI

A modern Shopping List REST API built with **Spring Boot** and **Kotlin**, following **Hexagonal Architecture** principles.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Project Structure](#project-structure)

## Overview

Lista-AI is a RESTful API designed to manage shopping lists and their items. It provides a clean and intuitive interface for creating, updating, and organizing your shopping needs.

### Features

- ✅ Create and manage multiple shopping lists
- ✅ Add, update, and remove items from lists
- ✅ Mark items as checked/unchecked
- ✅ RESTful API design with OpenAPI documentation
- ✅ Clean Architecture with Hexagonal design patterns

## Architecture

This project follows **Hexagonal Architecture** (also known as Ports and Adapters), which promotes separation of concerns and makes the application highly testable and maintainable.

```mermaid
graph TB
    subgraph External["🌐 External World"]
        Client["📱 Client Apps"]
        DB[(🗄️ Database)]
        ExtServices["🔌 External Services"]
    end

    subgraph Adapters["⚡ Adapters Layer"]
        subgraph InboundAdapters["Inbound Adapters (Driving)"]
            REST["🌐 REST Controllers"]
            GraphQL["📊 GraphQL (Future)"]
        end
        
        subgraph OutboundAdapters["Outbound Adapters (Driven)"]
            JPA["💾 JPA Repository"]
            Cache["⚡ Cache Adapter"]
        end
    end

    subgraph Ports["🔌 Ports Layer"]
        subgraph InboundPorts["Inbound Ports"]
            ShoppingListUC["📝 ShoppingListUseCase"]
            ItemUC["📦 ItemUseCase"]
        end
        
        subgraph OutboundPorts["Outbound Ports"]
            ShoppingListRepo["📚 ShoppingListRepository"]
            ItemRepo["📦 ItemRepository"]
        end
    end

    subgraph Domain["💎 Domain Layer"]
        Entities["🏛️ Entities"]
        ValueObjects["💠 Value Objects"]
        DomainServices["⚙️ Domain Services"]
    end

    Client --> REST
    REST --> ShoppingListUC
    REST --> ItemUC
    
    ShoppingListUC --> Entities
    ItemUC --> Entities
    
    ShoppingListUC --> ShoppingListRepo
    ItemUC --> ItemRepo
    
    ShoppingListRepo --> JPA
    ItemRepo --> JPA
    
    JPA --> DB
    Cache --> ExtServices

    style Domain fill:#e1f5fe,stroke:#01579b
    style Ports fill:#fff3e0,stroke:#e65100
    style Adapters fill:#f3e5f5,stroke:#7b1fa2
    style External fill:#e8f5e9,stroke:#2e7d32
```

### Architecture Layers

| Layer | Description |
|-------|-------------|
| **Domain** | Contains business entities, value objects, and domain services. This is the heart of the application with zero external dependencies. |
| **Ports** | Defines interfaces (ports) for inbound (use cases) and outbound (repositories) operations. |
| **Adapters** | Implements the ports. Inbound adapters handle external requests (REST, GraphQL), while outbound adapters handle persistence and external services. |

### Key Benefits

- 🧪 **Testability**: Business logic can be tested without infrastructure concerns
- 🔄 **Flexibility**: Easy to swap implementations (e.g., change database)
- 📦 **Modularity**: Clear separation between layers
- 🛡️ **Domain Protection**: Business rules are isolated and protected

## Tech Stack

| Technology | Purpose |
|------------|---------|
| **Kotlin** | Primary programming language |
| **Spring Boot 4.x** | Application framework |
| **Spring Data JPA** | Data persistence |
| **PostgreSQL** | Database (configurable) |
| **Gradle (Kotlin DSL)** | Build tool |
| **OpenAPI 3.0** | API documentation |

## Getting Started

### Prerequisites

- JDK 25 or higher
- Gradle 8.x
- Docker (optional, for database)

### Running the Application

```bash
# Clone the repository
git clone https://github.com/your-org/lista-ai.git
cd lista-ai

# Run with Gradle
./gradlew bootRun

# Or build and run the JAR
./gradlew build
java -jar build/libs/lista-ai-*.jar
```

### Running with Docker

```bash
docker-compose up -d
```

## API Endpoints

### Shopping Lists

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/v1/lists` | Create a new shopping list |
| `PUT` | `/v1/lists/{listId}` | Update a shopping list |
| `DELETE` | `/v1/lists/{listId}` | Delete a shopping list |

### Shopping List Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/v1/lists/{listId}/items` | List all items in a shopping list |
| `POST` | `/v1/lists/{listId}/items` | Add an item to a shopping list |
| `PUT` | `/v1/lists/{listId}/items/{itemId}` | Update an item |
| `DELETE` | `/v1/lists/{listId}/items/{itemId}` | Delete an item |

> 📖 Full API documentation available at `/swagger-ui.html` when the application is running.

## Project Structure

```
lista-ai/
├── src/main/kotlin/com/listaai/
│   ├── domain/                    # 💎 Domain Layer
│   │   ├── model/                 # Entities & Value Objects
│   │   │   ├── ShoppingList.kt
│   │   │   └── Item.kt
│   │   └── service/               # Domain Services
│   │
│   ├── application/               # 🔌 Application Layer (Ports)
│   │   ├── port/
│   │   │   ├── input/             # Inbound Ports (Use Cases)
│   │   │   │   ├── ShoppingListUseCase.kt
│   │   │   │   └── ItemUseCase.kt
│   │   │   └── output/            # Outbound Ports (Repositories)
│   │   │       ├── ShoppingListRepository.kt
│   │   │       └── ItemRepository.kt
│   │   └── service/               # Use Case Implementations
│   │       ├── ShoppingListService.kt
│   │       └── ItemService.kt
│   │
│   └── infrastructure/            # ⚡ Infrastructure Layer (Adapters)
│       ├── adapter/
│       │   ├── input/
│       │   │   └── rest/          # REST Controllers
│       │   │       ├── ShoppingListController.kt
│       │   │       └── ItemController.kt
│       │   └── output/
│       │       └── persistence/   # JPA Implementations
│       │           ├── entity/
│       │           ├── mapper/
│       │           └── repository/
│       └── config/                # Spring Configuration
│
├── src/main/resources/
│   ├── application.yml
│   └── openapi.yaml
│
└── build.gradle.kts
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.