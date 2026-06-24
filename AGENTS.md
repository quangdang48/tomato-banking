# AGENTS.md

Guidance for AI agents working in this repo. Read the relevant docs **before** writing code.

## Project

Tomato Banking — portfolio banking REST API. Spring Boot 3.3.8, Java 21, base package `com.tomato`. Port 8088. H2 (dev) → PostgreSQL (target). Money correctness is the central design constraint: every balance change is transactional, audited, idempotent, and concurrency-safe.

## Documentation map

Index: [docs/README.md](docs/README.md).

### Design — [docs/design/](docs/design/)
- [architecture.md](docs/design/architecture.md) — tech stack, 3-tier layering, package layout, cross-cutting concerns.
- [domain.md](docs/design/domain.md) — domain rules: money correctness, concurrency, idempotency, error handling, security.
- [database.md](docs/design/database.md) — data model, ER diagram, tables, indexes, migrations.
- [api.md](docs/design/api.md) — REST endpoints, request/response contracts, error codes, status mapping.

### Code conventions — [docs/code-convention/](docs/code-convention/)
- [README.md](docs/code-convention/README.md) — index + non-negotiables.
- [java.md](docs/code-convention/java.md) — naming, Lombok, DI, package layout.
- [exception.md](docs/code-convention/exception.md) — `ErrorCode` → `BusinessException` → `GlobalExceptionHandler`.
- [layers.md](docs/code-convention/layers.md) — controller / service / repository / entity / DTO rules.
- [banking.md](docs/code-convention/banking.md) — money, transactions, concurrency, idempotency.
- [best-practices.md](docs/code-convention/best-practices.md) — security, validation, logging, testing, performance.
- [testing.md](docs/code-convention/testing.md) — JUnit naming, display names, and test structure.

### Tests
- [k6-test/](k6-test/) — k6 load/concurrency tests for the lock strategies.

## Rules of engagement

- Follow [docs/code-convention/](docs/code-convention/). New code matches existing patterns or updates the convention doc.
- Money: `BigDecimal` only, compared with `compareTo`. Never `double`/`float`.
- Balance mutation is `@Transactional`; pair every balance change with a `Transaction` audit row.
- Services throw `BusinessException(ErrorCode)`; only `GlobalExceptionHandler` maps to HTTP status.
- Never expose entities over HTTP — map to a DTO via its `from(entity)` factory.
- Every response uses the `ApiResponse<T>` envelope.
- Constructor injection via `@RequiredArgsConstructor`; no field `@Autowired`.

## Build & run

```bash
mvn spring-boot:run        # run on :8088
mvn test                   # unit + integration tests
```

Swagger UI: `/swagger-ui.html` · H2 console: `/h2-console`.
