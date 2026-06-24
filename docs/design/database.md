# Database

## Engine

- **Dev**: H2 in-memory (`jdbc:h2:mem:dumydb`), `ddl-auto: create-drop`.
- **Target**: PostgreSQL, schema managed by Flyway (`ddl-auto: validate`).

Money columns use `NUMERIC(19,4)` (maps to Java `BigDecimal`). Never `float`/`double`.

## Naming

All physical table names use the `tbl_` prefix (`@Table(name = "tbl_users")`). The prefix applies to table names only — columns, indexes, and constraints are unprefixed. The logical names used in the tables below (`users`, `customer_profiles`, …) omit the prefix for readability; the physical name is always `tbl_` + the logical name (e.g. `users` → `tbl_users`). See [code-convention/java.md](../code-convention/java.md#naming).

## ER diagram

```
Banking core

┌────────────────┐          ┌────────────────────┐          ┌──────────────────┐
│ users          │          │ accounts           │          │ transactions     │
├────────────────┤          ├────────────────────┤          ├──────────────────┤
│ id          PK │──1:N──<  │ id              PK │──1:N──<  │ id            PK │
│ username    UK │          │ user_id         FK │          │ account_id    FK │
│ email       UK │          │ account_number  UK │          │ reference_id  UK │
└────────────────┘          └────────────────────┘          └──────────────────┘


Onboarding verification

┌────────────────┐
│ users          │
├────────────────┤
│ id          PK │
└───────┬────────┘
        │ 1:1
        ▼
┌───────────────────┐
│ customer_profiles │
├───────────────────┤
│ id              PK│
│ user_id      FK UK│
└─────────┬─────────┘
          │
          ├──1:1──►  kyc_verifications        (profile_id FK UK)
          ├──1:1──►  kyb_verifications        (profile_id FK UK)
          ├──1:N──►  verification_documents   (profile_id FK · owner_id FK→beneficial_owners 0..1)
          └──1:N──►  onboarding_audit_logs    (profile_id FK · actor_user_id FK→users)

kyb_verifications  ──1:N──►  beneficial_owners   (kyb_verification_id FK)
```

Legend: `PK` = primary key, `FK` = foreign key, `UK` = unique key. `1:N` parent→child,
`1:1` one-to-one, `0..1` optional. The `<` / `►` head points at the child (many) side.

## Tables

### users
Maps to existing `User` entity (`com.tomato.modules.user.entity.User`).

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

### customer_profiles (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users.id, NOT NULL, UNIQUE |
| customer_type | VARCHAR | NOT NULL (INDIVIDUAL / BUSINESS) |
| status | VARCHAR | NOT NULL (DRAFT / SUBMITTED / IN_REVIEW / APPROVED / REJECTED / REQUIRES_MORE_INFO) |
| risk_level | VARCHAR | nullable (LOW / MEDIUM / HIGH) |
| submitted_at | TIMESTAMP | nullable |
| reviewed_at | TIMESTAMP | nullable |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

One authenticated user has one onboarding profile in this phase.

### kyc_verifications (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK → customer_profiles.id, NOT NULL, UNIQUE |
| legal_name | VARCHAR | NOT NULL |
| date_of_birth | DATE | NOT NULL |
| document_type | VARCHAR | NOT NULL (CCCD / PASSPORT) |
| document_number | VARCHAR | NOT NULL |
| street_address | VARCHAR | NOT NULL |
| district | VARCHAR | NOT NULL |
| province_city | VARCHAR | NOT NULL |
| status | VARCHAR | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

For Vietnam eKYC, enforce CCCD format at the DB layer:

```sql
CONSTRAINT chk_cccd_format CHECK (
    document_type != 'CCCD' OR document_number ~ '^[0-9]{12}$'
)
```

### kyb_verifications (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK → customer_profiles.id, NOT NULL, UNIQUE |
| legal_business_name | VARCHAR | NOT NULL |
| registration_number | VARCHAR | NOT NULL |
| tax_id | VARCHAR | nullable |
| incorporation_date | DATE | nullable |
| business_address_line1 | VARCHAR | NOT NULL |
| district | VARCHAR | NOT NULL |
| province_city | VARCHAR | NOT NULL |
| industry | VARCHAR | nullable |
| status | VARCHAR | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

For Vietnam KYB, enforce tax ID format at the DB layer:

```sql
CONSTRAINT chk_tax_id_format CHECK (
    tax_id ~ '^[0-9]{10}$' OR tax_id ~ '^[0-9]{10}-[0-9]{3}$'
)
```

### beneficial_owners (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| kyb_verification_id | BIGINT | FK → kyb_verifications.id, NOT NULL |
| legal_name | VARCHAR | NOT NULL |
| date_of_birth | DATE | NOT NULL |
| ownership_percentage | NUMERIC(5,2) | NOT NULL, CHECK between 0 and 100 |
| document_type | VARCHAR | NOT NULL (CCCD / PASSPORT) |
| document_number | VARCHAR | NOT NULL |
| status | VARCHAR | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

Use `BigDecimal` for `ownership_percentage`, compared with `compareTo`.

### verification_documents (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK → customer_profiles.id, NOT NULL |
| owner_id | BIGINT | FK → beneficial_owners.id, nullable |
| document_type | VARCHAR | NOT NULL |
| storage_key | VARCHAR | NOT NULL |
| original_filename | VARCHAR | NOT NULL |
| content_type | VARCHAR | NOT NULL |
| size_bytes | BIGINT | NOT NULL |
| status | VARCHAR | NOT NULL (UPLOADED / ACCEPTED / REJECTED) |
| rejection_reason | VARCHAR | nullable |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

Store files outside the database. This table stores metadata and a private storage key.

### onboarding_audit_logs (planned)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK → customer_profiles.id, NOT NULL |
| actor_user_id | BIGINT | FK → users.id, nullable |
| action | VARCHAR | NOT NULL |
| old_status | VARCHAR | nullable |
| new_status | VARCHAR | nullable |
| reason | VARCHAR | nullable |
| created_at | TIMESTAMP | NOT NULL |

Every onboarding status transition writes an audit row in the same transaction.

## Indexes

| Table | Index | Why |
|-------|-------|-----|
| users | unique(username), unique(email) | login lookup, dup prevention |
| accounts | unique(account_number) | lookup by number |
| accounts | idx(user_id) | list a user's accounts |
| transactions | unique(reference_id) | idempotency |
| transactions | idx(account_id, created_at) | paginated history |
| customer_profiles | unique(user_id) | one profile per user |
| customer_profiles | idx(status, updated_at) | reviewer queue |
| kyc_verifications | unique(profile_id) | one KYC record per profile |
| kyb_verifications | unique(profile_id) | one KYB record per profile |
| beneficial_owners | idx(kyb_verification_id) | list owners for KYB |
| verification_documents | idx(profile_id, document_type) | required document checks |
| onboarding_audit_logs | idx(profile_id, created_at) | audit timeline |

## Locking

- **Optimistic**: `accounts.version` (`@Version`). Conflict → `OptimisticLockException` → HTTP 409.
- **Pessimistic**: repository read with `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`.

## Migration strategy

- Dev: H2 `create-drop` for fast iteration.
- Prod: Flyway. Versioned scripts under `src/main/resources/db/migration`:
  - `V1__auth_and_kyc.sql` — `users`, `customer_profiles`, `kyc_verifications`, `verification_documents`, `onboarding_audit_logs`
  - `V2__kyb_extension.sql` — `kyb_verifications`, `beneficial_owners`, `verification_documents.owner_id`
  - `V3__banking_core.sql` — `accounts`, `transactions`
- Set `ddl-auto: validate` so Hibernate checks schema matches but never mutates it.

## Seed (dev)

Insert a demo user + account to exercise deposit/withdraw without registering each run. Place in `V900__seed_dev.sql` (dev profile only) or `data.sql`.
