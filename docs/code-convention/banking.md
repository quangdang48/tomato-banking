# Banking Conventions

Money-correctness rules. These override convenience — a rounding bug or a race that drives a balance negative is a hard review fail. See [domain.md](../design/domain.md) for the domain rationale.

---

## 1. Money type — always `BigDecimal`

Never `double`/`float` for `balance` or `amount` — binary floating point loses cents.

```java
// Good
@Column(nullable = false, precision = 19, scale = 4)
private BigDecimal balance;

// Bad — never
private double balance;
```

Rules:
- DB column: `NUMERIC(19,4)` → JPA `precision = 19, scale = 4`.
- Compare with `compareTo`, never `equals` (`equals` is scale-sensitive: `1.0 != 1.00`).
- Construct from `String`, not `double`: `new BigDecimal("0.1")`, not `new BigDecimal(0.1)`.
- Set an explicit `RoundingMode` on any division.

```java
if (account.getBalance().compareTo(amount) < 0) {
    throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);   // 422 / 2100
}
account.setBalance(account.getBalance().subtract(amount));
```

---

## 2. Transaction atomicity — `@Transactional`

"Update balance + write the audit record" is one unit. Both commit or both roll back. Never a balance change without a matching `Transaction` row.

```java
@Override
@Transactional
public WithdrawResult withdraw(Long accountId, WithdrawRequest req) {
    Account account = loadForUpdate(accountId);     // lock strategy below
    assertSufficientFunds(account, req.getAmount());

    account.setBalance(account.getBalance().subtract(req.getAmount()));

    Transaction tx = Transaction.builder()
        .account(account)
        .type(TransactionType.WITHDRAW)
        .amount(req.getAmount())
        .balanceAfter(account.getBalance())
        .referenceId(req.getReferenceId())
        .status(TransactionStatus.COMPLETED)
        .build();
    transactionRepository.save(tx);                 // same tx as the balance update

    return WithdrawResult.from(tx);
}
```

Rules:
- `@Transactional` on the service write method, not the controller.
- `BusinessException` is a `RuntimeException` → rolls back automatically.
- Keep money mutations **synchronous**. Side effects (notifications, audit fan-out) go async via `ThreadPoolConfig` **after** commit.
- Read-only queries: `@Transactional(readOnly = true)`.

---

## 3. Concurrency control

Two concurrent withdrawals must never drive balance negative. Pick **one** strategy per operation and document the choice in the service method's javadoc.

### Optimistic (default — low contention)

`@Version` column. Conflicting write throws `OptimisticLockException` → map to `409` / `2103`; client retries.

```java
@Entity
@Table(name = "tbl_accounts")
public class Account {
    @Version
    private Long version;
}
```

Map the framework exception to the envelope (add to `GlobalExceptionHandler`):

```java
@ExceptionHandler({ OptimisticLockException.class, ObjectOptimisticLockingFailureException.class })
public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
    return new ResponseEntity<>(
        ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT),   // 2103
        HttpStatus.CONFLICT);
}
```

### Pessimistic (high contention)

`SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`. Serializes writers on the row.

```java
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
```

| Strategy | Use when | Cost |
|----------|----------|------|
| Optimistic `@Version` | low contention, default | retries on conflict |
| Pessimistic `FOR UPDATE` | high contention on a hot row | holds DB lock, lower throughput |

**Test requirement:** every money mutation has a concurrency test spawning N parallel threads (or a k6 run — see `k6-test/`) asserting the final balance is exactly `initial − Σ(applied)` and never negative.

---

## 4. Idempotency — `referenceId`

Client sends a `referenceId` (idempotency key) on every deposit/withdraw. Replaying the same key returns the original result instead of double-applying. Defends against retries / double-clicks.

Rules:
- `Transaction.referenceId` has a **unique constraint** (per account).
- On write: look up the key first; if present, return the stored result. Otherwise apply and persist.
- A unique-constraint violation on a concurrent insert maps to `409` / `2102`.

```java
@Column(name = "reference_id", nullable = false, unique = true)
private String referenceId;
```

```java
Optional<Transaction> existing = transactionRepository.findByReferenceId(req.getReferenceId());
if (existing.isPresent()) {
    return WithdrawResult.from(existing.get());   // replay — no double-apply
}
```

---

## 5. Validation rules (enforce in DTO + service)

| Rule | Where | On failure |
|------|-------|-----------|
| `amount > 0` | `@Positive` on DTO + service check | 400 / 2101 |
| `amount <= balance` (withdraw) | service | 422 / 2100 |
| `referenceId` required + unique | `@NotBlank` + DB constraint | 409 / 2102 |
| `currency` ISO 4217 (3 letters) | `@Pattern("[A-Z]{3}")` | 400 |
| account owned by caller | service (from JWT principal) | 403 / 2104 |
| account `ACTIVE` | service | 409 / 2105 |

---

## 6. Error codes (banking)

Defined in `ErrorCode`, mapped in `GlobalExceptionHandler` (see [exception.md](exception.md)). Banking codes start at `2100`:

| code | name (suggested) | Meaning | HTTP |
|------|------------------|---------|------|
| 2100 | `INSUFFICIENT_FUNDS` | balance < amount | 422 |
| 2101 | `AMOUNT_NOT_POSITIVE` | amount <= 0 | 400 |
| 2102 | `DUPLICATE_REFERENCE_ID` | idempotency replay collision | 409 |
| 2103 | `OPTIMISTIC_LOCK_CONFLICT` | version conflict — retry | 409 |
| 2104 | `ACCOUNT_NOT_OWNED` | caller is not the owner | 403 |
| 2105 | `ACCOUNT_NOT_ACTIVE` | frozen / closed | 409 |

---

## Banking checklist (every money endpoint)

- [ ] `BigDecimal`, compared with `compareTo`
- [ ] `@Transactional` wraps balance change + audit insert
- [ ] one concurrency strategy chosen and documented
- [ ] `referenceId` idempotency enforced (unique constraint + lookup)
- [ ] amount > 0 and funds checked → correct `ErrorCode`
- [ ] ownership checked against the JWT principal
- [ ] concurrency test (N threads / k6) asserts non-negative, exact final balance
