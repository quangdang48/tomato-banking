# Phased Delivery Plan

This plan keeps onboarding and banking buildable in slices. Do not implement all KYC,
KYB, documents, accounts, and transactions in one sprint. Each phase has a clear user
flow, a small set of tables, and a matching Flyway migration.

Conventions this follows: [layers.md](../code-convention/layers.md),
[exception.md](../code-convention/exception.md),
[banking.md](../code-convention/banking.md). Database target:
[database.md](../design/database.md).

---

## Phase 1 - Base Auth and Individual eKYC

Goal: users can register/login, create an `INDIVIDUAL` onboarding profile, submit KYC
identity data, and attach identity-document metadata. No KYB, no accounts, no money
movement yet.

Flow:

```text
Register -> create User -> create INDIVIDUAL profile -> enter KYC -> add CCCD/passport documents -> submit
```

Migration: `V1__auth_and_kyc.sql`

Tables:

| Table | Purpose |
|-------|---------|
| `users` | Login/registration identity |
| `customer_profiles` | Onboarding hub; Phase 1 only uses `INDIVIDUAL` |
| `kyc_verifications` | Individual identity details, optimized for Vietnam CCCD/passport |
| `verification_documents` | Metadata for CCCD/passport images |
| `onboarding_audit_logs` | Status transition audit trail |

Suggested indexes:

```sql
CREATE INDEX idx_profiles_status ON customer_profiles(status, updated_at);
CREATE INDEX idx_docs_profile ON verification_documents(profile_id, document_type);
```

Implementation checklist:

1. Add auth/JWT foundation if not already complete.
2. Add profile creation for `INDIVIDUAL`.
3. Add KYC upsert with CCCD format validation.
4. Add document metadata endpoint.
5. Add submit/review status transitions.
6. Add audit log writes for every status transition.
7. Test duplicate profile, missing KYC fields, missing documents, and approval flow.

---

## Phase 2 - Business Onboarding Extension

Goal: extend the onboarding system for `BUSINESS` customers. This phase reuses
`customer_profiles`, `verification_documents`, and `onboarding_audit_logs` from Phase 1.

Flow:

```text
Create BUSINESS profile -> enter KYB -> add beneficial owners -> attach company/owner documents -> submit
```

Migration: `V2__kyb_extension.sql`

Tables/changes:

| Table/change | Purpose |
|--------------|---------|
| `kyb_verifications` | Company identity, registration number, Vietnam tax ID |
| `beneficial_owners` | UBO/shareholder identity and ownership percentage |
| `verification_documents.owner_id` | Optional link from a document to a beneficial owner |

Suggested indexes:

```sql
CREATE INDEX idx_bo_kyb_id ON beneficial_owners(kyb_verification_id);
```

Implementation checklist:

1. Add profile creation for `BUSINESS`.
2. Add KYB upsert with Vietnam tax-id validation.
3. Add beneficial-owner create/update/delete.
4. Allow document metadata to attach to either profile or owner.
5. Require at least one beneficial owner before submit.
6. Test business submit, missing owner, invalid tax ID, and owner document attachment.

---

## Phase 3 - Banking Core and Transaction Processing

Goal: after onboarding is `APPROVED`, users can receive an account and perform money
operations. This phase introduces the money-correctness rules: transactional balance
updates, audit transaction rows, idempotency, and locking.

Flow:

```text
Approved onboarding -> create account -> deposit/withdraw -> transaction history
```

Migration: `V3__banking_core.sql`

Tables:

| Table | Purpose |
|-------|---------|
| `accounts` | User account, balance, currency, status, optimistic-lock version |
| `transactions` | Money-movement audit trail and idempotency record |

Suggested indexes:

```sql
CREATE INDEX idx_accounts_user ON accounts(user_id);
CREATE INDEX idx_tx_account_time ON transactions(account_id, created_at DESC);
```

Implementation checklist:

1. Add account entity/repository/service/controller.
2. Gate account creation with `onboardingService.requireApproved(userId)`.
3. Add deposit/withdraw with `@Transactional`.
4. Write a `Transaction` row for every balance change.
5. Enforce `referenceId` idempotency.
6. Add optimistic or pessimistic locking strategy.
7. Test insufficient funds, duplicate reference ID, concurrent withdrawals, and transaction history.

---

## Why this split

- Each phase has a focused demo path.
- Flyway migrations stay additive and easy to reason about.
- KYC can be completed and tested before KYB complexity lands.
- Banking money logic only starts after onboarding is stable.
- Regression tests can grow in layers instead of trying to cover everything at once.
