# Architecture

## Overview

Classic 3-tier Spring Boot REST service. Stateless API, JWT auth, relational store. Money correctness is the central design constraint — every balance change is transactional, audited, and concurrency-safe.

```
Client ──HTTP/JSON──> Controller ──> Service ──> Repository ──> DB
                          │            │
                       DTO map     business rules
                                   (@Transactional)
```

## Tech stack

| Concern | Choice | Notes |
|---------|--------|-------|
| Framework | Spring Boot 3.3.8 | Java 21 |
| Web | spring-boot-starter-web | REST controllers |
| Persistence | Spring Data JPA + Hibernate | |
| DB (dev) | H2 in-memory | fast local run |
| DB (prod target) | PostgreSQL | real concurrency, locks |
| Validation | spring-boot-starter-validation | Bean Validation (Jakarta) |
| Boilerplate | Lombok 1.18.34 | getters/builders |
| API docs | springdoc-openapi 2.5.0 | Swagger UI |
| Security (planned) | Spring Security + JWT | stateless auth |
| Migration (planned) | Flyway | versioned schema |
| Mapping (optional) | MapStruct | Entity ↔ DTO |
| Test | JUnit 5 + Mockito + Testcontainers | race-condition + integration |

## Layers

**Controller** — HTTP only. Validate input (`@Valid`), call service, wrap result in `ApiResponse`. No business logic.

**Service** — business rules + transaction boundaries (`@Transactional`). Owns money logic: balance checks, idempotency, audit record creation. Interface + `*Impl` split (see `UserService` / `UserServiceImpl`).

**Repository** — Spring Data JPA interfaces. Custom queries for locking (`@Lock`).

**Entity** — JPA `@Entity` mapped to tables.

**DTO** — request/response objects. Never expose entities directly.

## Package layout

```
com.dumy
├── config/        SwaggerConfig, ThreadPoolConfig, (SecurityConfig planned)
├── controller/    UserController, (AccountController, AuthController planned)
├── service/       UserService(+Impl), (AccountService, TransactionService planned)
├── repository/    UserRepository, (AccountRepository, TransactionRepository planned)
├── entity/        User, (Account, Transaction planned)
├── dto/           CreateUserRequest, UpdateUserRequest, UserResponse, ...
├── common/        ApiResponse (uniform envelope)
├── exception/     GlobalExceptionHandler, BusinessException, ErrorCode, ObjectsValidator
└── Main.java
```

## Cross-cutting concerns

### Money correctness
- `balance` and `amount` use **`BigDecimal`**, never `double`/`float`. Float rounding loses money — classic banking review fail.
- Store as `NUMERIC(19,4)` in DB.

### Transaction atomicity
- `@Transactional` wraps "update balance + write transaction log" as one unit. Either both commit or both roll back. No balance update without a matching audit record.

### Concurrency control
Two concurrent withdrawals must never drive balance negative.

- **Optimistic** — `@Version` column on `Account`. Conflicting write throws `OptimisticLockException` → map to HTTP 409, client retries. Good default, low contention.
- **Pessimistic** — `SELECT ... FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the balance read. Serializes writers on a row. Use under high contention.

Pick one per operation; document it. Cover with a test spawning N parallel threads asserting final balance is correct.

### Idempotency
- Client sends an idempotency key (`referenceId`) on deposit/withdraw.
- Server stores it on `Transaction` with a unique constraint. Replay of the same key returns the original result instead of double-applying. Defends against retries / double-clicks.

### Error handling
- Central `GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to HTTP status + `ErrorCode`.
- `BusinessException` carries an `ErrorCode`. See [api.md](api.md) for the status map.

### Response envelope
- All responses wrapped in `ApiResponse` for a uniform `{ code, message, data }` shape.

### Async
- `ThreadPoolConfig` provides an executor for non-blocking side work (e.g. notifications, audit fan-out). Keep money mutations synchronous and transactional.

### Security (planned)
- Stateless JWT. `POST /api/auth/login` issues token; filter validates on each request.
- Account endpoints scoped to the authenticated owner — user A cannot touch user B's account.
- Passwords stored as bcrypt hash (`password_hash`), never plaintext.

## Config (dev)

```yaml
server.port: 8088
spring.datasource.url: jdbc:h2:mem:dumydb
spring.jpa.hibernate.ddl-auto: create-drop   # dev only; Flyway in prod
```

Prod: swap H2 → Postgres, set `ddl-auto: validate`, manage schema with Flyway.
