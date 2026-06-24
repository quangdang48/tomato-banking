# Domain Knowledge

## Money correctness

- `balance` and `amount` use **`BigDecimal`**, never `double`/`float`. Float rounding loses money — classic banking review fail.
- Store as `NUMERIC(19,4)` in DB.

## Transaction atomicity

`@Transactional` wraps "update balance + write transaction log" as one unit. Either both commit or both roll back. No balance update without a matching audit record.

## Concurrency control

Two concurrent withdrawals must never drive balance negative.

- **Optimistic** — `@Version` column on `Account`. Conflicting write throws `OptimisticLockException` → map to HTTP 409, client retries. Good default, low contention.
- **Pessimistic** — `SELECT ... FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the balance read. Serializes writers on a row. Use under high contention.

Pick one per operation; document it. Cover with a test spawning N parallel threads asserting final balance is correct.

## Idempotency

- Client sends an idempotency key (`referenceId`) on deposit/withdraw.
- Server stores it on `Transaction` with a unique constraint. Replay of the same key returns the original result instead of double-applying. Defends against retries / double-clicks.

## Error handling

- Central `GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to HTTP status + `ErrorCode`.
- `BusinessException` carries an `ErrorCode`. See [api.md](api.md) for the status map.

## Response envelope

All responses wrapped in `ApiResponse` for a uniform `{ code, message, data }` shape.

## Async

`ThreadPoolConfig` provides an executor for non-blocking side work (e.g. notifications, audit fan-out). Keep money mutations synchronous and transactional.

## Security

- Stateless JWT. `POST /api/auth/login` issues token; filter validates on each request.
- Account endpoints scoped to the authenticated owner — user A cannot touch user B's account.
- Passwords stored as bcrypt hash (`password_hash`), never plaintext.

## Onboarding (KYC / KYB)

Onboarding verifies whether an authenticated user is allowed to bank. Keep it separate
from account and transaction logic, but enforce its decision before account activation and
money movement.

- Individual customers complete **KYC**.
- Business customers complete **KYB** and provide beneficial owners.
- One `CustomerProfile` belongs to one `User` in this phase.
- Profile status flow: `DRAFT` → `SUBMITTED` → `IN_REVIEW` → `APPROVED` / `REJECTED` /
  `REQUIRES_MORE_INFO`.
- Customers may edit onboarding data only in `DRAFT` or `REQUIRES_MORE_INFO`.
- Account creation and deposit/withdraw require onboarding status `APPROVED`.
- Every onboarding status transition writes an audit log row.
- Do not expose sensitive identity fields, document numbers, tax IDs, or storage keys in
  public responses.

Detailed implementation plan: [phase-2-onboarding-kyc-kyb.md](../implementation/phase-2-onboarding-kyc-kyb.md).
Delivery sequence: [phased-delivery-plan.md](../implementation/phased-delivery-plan.md).
