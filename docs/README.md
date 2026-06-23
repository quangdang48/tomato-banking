# Tomato Banking — Documentation

Portfolio banking REST API built with Spring Boot. This folder holds the design docs and code conventions.

### Design — [design/](design/)

| Doc | What's inside |
|-----|---------------|
| [design/architecture.md](design/architecture.md) | Tech stack, layering, package layout, cross-cutting concerns (concurrency, idempotency, transactions, security). |
| [design/domain.md](design/domain.md) | Domain rules: money correctness, concurrency, idempotency, error handling, security. |
| [design/database.md](design/database.md) | Data model, ER diagram, table definitions, indexes, migration strategy. |
| [design/api.md](design/api.md) | REST endpoints, request/response contracts, error codes, status mapping. |

### Code conventions — [code-convention/](code-convention/)

| Doc | What's inside |
|-----|---------------|
| [code-convention/README.md](code-convention/README.md) | Index + non-negotiables. |
| [code-convention/java.md](code-convention/java.md) | Naming, Lombok, DI, package layout. |
| [code-convention/exception.md](code-convention/exception.md) | `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`. |
| [code-convention/layers.md](code-convention/layers.md) | Controller / service / repository / entity / DTO rules. |
| [code-convention/banking.md](code-convention/banking.md) | Money, transactions, concurrency, idempotency. |
| [code-convention/best-practices.md](code-convention/best-practices.md) | Security, validation, logging, testing, performance. |

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
