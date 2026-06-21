# Tomato Banking — Documentation

Portfolio banking REST API built with Spring Boot. This folder holds the design docs.

| Doc | What's inside |
|-----|---------------|
| [architecture.md](architecture.md) | Tech stack, layering, package layout, cross-cutting concerns (concurrency, idempotency, transactions, security). |
| [database.md](database.md) | Data model, ER diagram, table definitions, indexes, migration strategy. |
| [api.md](api.md) | REST endpoints, request/response contracts, error codes, status mapping. |

## Quick facts

- **Group / base package**: `com.dumy`
- **Java**: 21
- **Spring Boot**: 3.3.8
- **DB (dev)**: H2 in-memory (`jdbc:h2:mem:dumydb`)
- **DB (target)**: PostgreSQL
- **Port**: 8088
- **API docs**: Swagger UI at `/swagger-ui.html`
- **H2 console**: `/h2-console`

## Roadmap

1. Setup project + entities + repositories + migration
2. Basic account CRUD (no auth yet)
3. Deposit / withdraw + balance logic + validation
4. Concurrency handling (race-condition tests)
5. Auth (JWT) + per-user endpoint protection
6. Standard exception handling (400 / 404 / 409 / 422)
7. Unit + integration tests (Testcontainers + Postgres)
8. Swagger docs + README polish

## Current state

Skeleton exists: `User` entity, `UserController`, `UserService`, global exception handling, Swagger config, thread-pool config. Banking entities (`Account`, `Transaction`) and money logic are the next build steps.
