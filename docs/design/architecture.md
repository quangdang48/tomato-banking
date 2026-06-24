# Architecture

## Overview

Classic 3-tier Spring Boot REST service. Stateless API, JWT auth, relational store. Money correctness is the central design constraint — every balance change is transactional, audited, and concurrency-safe.

See [domain.md](domain.md) for domain rules (money correctness, concurrency, idempotency, etc.).

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CLIENT                                    │
│                    HTTP/JSON  (port 8088)                           │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      FILTER CHAIN                                   │
│                                                                     │
│   ┌─────────────────────────────────────────┐                       │
│   │  JwtAuthenticationFilter  (planned)     │                       │
│   │  • Extract Bearer token                 │                       │
│   │  • Validate signature + expiry          │                       │
│   │  • Set SecurityContext                  │                       │
│   └─────────────────────────────────────────┘                       │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       CONTROLLER LAYER                              │
│                                                                     │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────────┐   │
│  │ AuthCtrl     │  │ UserController  │  │ AccountController    │   │
│  │ POST /login  │  │ POST /users     │  │ GET  /accounts       │   │
│  │ POST /refresh│  │ GET  /users/:id │  │ POST /deposit        │   │
│  │  (planned)   │  │ PUT  /users/:id │  │ POST /withdraw       │   │
│  └──────┬───────┘  └──────┬──────────┘  │  (planned)           │   │
│         │                 │             └──────────┬───────────-┘   │
│         │   @Valid DTO     │                        │                │
│         │   ApiResponse    │                        │                │
└─────────┼─────────────────┼────────────────────────┼────────────────┘
          │                 │                        │
          ▼                 ▼                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        SERVICE LAYER                                │
│                      (@Transactional)                               │
│                                                                     │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────────┐   │
│  │ AuthService  │  │ UserService     │  │ AccountService       │   │
│  │  (planned)   │  │ + UserServiceImpl│  │ TransactionService   │   │
│  │              │  │                 │  │  (planned)           │   │
│  │ • issue JWT  │  │ • create user   │  │ • balance check      │   │
│  │ • validate   │  │ • update user   │  │ • debit / credit     │   │
│  │   password   │  │ • fetch user    │  │ • write audit record │   │
│  └──────┬───────┘  └──────┬──────────┘  │ • idempotency check  │   │
│         │                 │             └──────────┬────────────┘   │
│         │  business rules + lock strategy          │                │
└─────────┼─────────────────┼────────────────────────┼────────────────┘
          │                 │                        │
          ▼                 ▼                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      REPOSITORY LAYER                               │
│                   (Spring Data JPA)                                 │
│                                                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ UserRepository   │  │ AccountRepository│  │ TxRepository     │  │
│  │                  │  │                  │  │  (planned)       │  │
│  │ findByUsername() │  │ findById()       │  │ findByRefId()    │  │
│  │ findByEmail()    │  │  @Lock(PESSIM.)  │  │  (idempotency)   │  │
│  │                  │  │  @Version(OPT.)  │  │                  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘  │
└───────────┼─────────────────────┼─────────────────────┼────────────┘
            │                     │                     │
            ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          DATABASE                                   │
│                                                                     │
│   Dev: H2 in-memory  │  Prod: PostgreSQL + Flyway migrations        │
│                                                                     │
│   ┌───────────┐       ┌─────────────┐       ┌──────────────────┐   │
│   │  users    │──────<│  accounts   │──────<│  transactions    │   │
│   │  (1)      │  1:N  │  (planned)  │  1:N  │  (planned)       │   │
│   └───────────┘       └─────────────┘       └──────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘

Cross-cutting (all layers):
  ┌──────────────────────────┐   ┌──────────────────────────────────┐
  │  GlobalExceptionHandler  │   │  ThreadPoolConfig                │
  │  @RestControllerAdvice   │   │  async executor for side effects │
  │  BusinessException       │   │  (notifications, audit fan-out)  │
  │  → HTTP status + code    │   │  money mutations stay sync       │
  └──────────────────────────┘   └──────────────────────────────────┘
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
com.tomato
├── common/        ApiResponse (uniform envelope)
├── config/        SwaggerConfig, ThreadPoolConfig, JwtProperties
├── exception/     GlobalExceptionHandler, BusinessException, ErrorCode, ObjectsValidator
├── modules/
│   ├── auth/
│   │   ├── config/      PasswordEncoderConfig
│   │   ├── controller/  AuthController
│   │   ├── dto/         request/, response/
│   │   ├── security/    JwtAuthenticationFilter
│   │   └── service/     AuthService(+Impl), JwtService
│   └── user/
│       ├── controller/  UserController
│       ├── dto/         request/, response/
│       ├── entity/      User
│       ├── repository/  UserRepository
│       └── service/     UserService(+Impl)
└── Main.java
```

## Cross-cutting concerns

See [domain.md](domain.md) for full rules on: money correctness, transaction atomicity, concurrency control, idempotency, error handling, response envelope, async, security.

## Config (dev)

```yaml
server.port: 8088
spring.datasource.url: jdbc:h2:mem:dumydb
spring.jpa.hibernate.ddl-auto: create-drop   # dev only; Flyway in prod
```

Prod: swap H2 → Postgres, set `ddl-auto: validate`, manage schema with Flyway.
