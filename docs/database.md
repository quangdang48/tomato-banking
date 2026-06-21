# Database

## Engine

- **Dev**: H2 in-memory (`jdbc:h2:mem:dumydb`), `ddl-auto: create-drop`.
- **Target**: PostgreSQL, schema managed by Flyway (`ddl-auto: validate`).

Money columns use `NUMERIC(19,4)` (maps to Java `BigDecimal`). Never `float`/`double`.

## ER diagram

```
┌──────────────┐        ┌────────────────┐        ┌──────────────────┐
│    users     │ 1    * │    accounts    │ 1    * │   transactions   │
├──────────────┤        ├────────────────┤        ├──────────────────┤
│ id (PK)      │───────<│ id (PK)        │───────<│ id (PK)          │
│ username  U  │        │ user_id (FK)   │        │ account_id (FK)  │
│ email     U  │        │ account_number │        │ type             │
│ full_name    │        │ balance        │        │ amount           │
│ password_hash│        │ currency       │        │ balance_after    │
│ created_at   │        │ status         │        │ status           │
└──────────────┘        │ version        │        │ reference_id  U  │
                        │ created_at     │        │ created_at       │
                        │ updated_at     │        └──────────────────┘
                        └────────────────┘
        U = unique
```

## Tables

### users
Maps to existing `User` entity (`com.dumy.entity.User`).

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT / IDENTITY | PK |
| username | VARCHAR | NOT NULL, UNIQUE |
| email | VARCHAR | NOT NULL, UNIQUE |
| full_name | VARCHAR | nullable |
| password_hash | VARCHAR | NOT NULL (bcrypt) |
| created_at | TIMESTAMP | NOT NULL, default now |

> Current entity uses `Integer id` (IDENTITY). Recommend `BIGINT` for prod.

### accounts (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users.id, NOT NULL |
| account_number | VARCHAR | NOT NULL, UNIQUE |
| balance | NUMERIC(19,4) | NOT NULL, default 0, CHECK >= 0 |
| currency | VARCHAR(3) | NOT NULL (ISO 4217, e.g. VND, USD) |
| status | VARCHAR | NOT NULL (ACTIVE / FROZEN / CLOSED) |
| version | BIGINT | NOT NULL — optimistic lock (`@Version`) |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

`CHECK (balance >= 0)` is a DB-level safety net behind app validation.

### transactions (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| account_id | BIGINT | FK → accounts.id, NOT NULL |
| type | VARCHAR | NOT NULL (DEPOSIT / WITHDRAW) |
| amount | NUMERIC(19,4) | NOT NULL, CHECK > 0 |
| balance_after | NUMERIC(19,4) | NOT NULL — snapshot post-apply |
| status | VARCHAR | NOT NULL (PENDING / COMPLETED / FAILED) |
| reference_id | VARCHAR | NOT NULL, UNIQUE — idempotency key |
| created_at | TIMESTAMP | NOT NULL |

`reference_id` UNIQUE enforces idempotency at the DB layer: a replayed request collides and is rejected/short-circuited.

## Indexes

| Table | Index | Why |
|-------|-------|-----|
| users | unique(username), unique(email) | login lookup, dup prevention |
| accounts | unique(account_number) | lookup by number |
| accounts | idx(user_id) | list a user's accounts |
| transactions | unique(reference_id) | idempotency |
| transactions | idx(account_id, created_at) | paginated history |

## Locking

- **Optimistic**: `accounts.version` (`@Version`). Conflict → `OptimisticLockException` → HTTP 409.
- **Pessimistic**: repository read with `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`.

## Migration strategy

- Dev: H2 `create-drop` for fast iteration.
- Prod: Flyway. Versioned scripts under `src/main/resources/db/migration`:
  - `V1__create_users.sql`
  - `V2__create_accounts.sql`
  - `V3__create_transactions.sql`
- Set `ddl-auto: validate` so Hibernate checks schema matches but never mutates it.

## Seed (dev)

Insert a demo user + account to exercise deposit/withdraw without registering each run. Place in `V900__seed_dev.sql` (dev profile only) or `data.sql`.
