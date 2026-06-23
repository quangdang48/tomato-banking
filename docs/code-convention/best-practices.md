# Backend Best Practices

General backend guidance beyond the codebase-specific rules. Apply when adding the planned auth/account/transaction features. Banking-specific rules live in [banking.md](banking.md).

---

## Security

### Passwords

- Store **bcrypt** hashes only, never plaintext. The `User.passwordHash` column exists; the current `createUser` stores the raw password — fix before auth ships.

```java
private final PasswordEncoder passwordEncoder;   // BCryptPasswordEncoder bean

String hash = passwordEncoder.encode(request.getPassword());
User user = User.builder().passwordHash(hash).build();
```

### JWT auth (planned)

- Stateless: `POST /api/auth/login` issues a token; a `JwtAuthenticationFilter` validates signature + expiry on each request and sets the `SecurityContext`.
- Keep the signing secret out of source — load from env/`application.yaml` placeholder, never hard-code.
- Short access-token TTL (`expiresIn: 3600`); add refresh later if needed.

### Authorization

- Account endpoints scoped to the authenticated owner. Resolve the principal from the `SecurityContext`, compare to the resource owner, throw `ACCOUNT_NOT_OWNED` (403) on mismatch.
- Never trust an id from the request body for ownership — derive identity from the validated token.

### Don't leak internals

- `GlobalExceptionHandler` returns a generic message on `500`; log the real cause server-side. Never return stack traces or SQL to the client.
- Don't expose `passwordHash`, `version`, or internal ids you don't intend to be public in response DTOs.

---

## Validation

- Structural validation on the DTO with Bean Validation annotations; trigger with `@Valid` in the controller.
- Business validation in the service (uniqueness, funds, ownership) — annotations can't see the DB.

```java
@Getter
@NoArgsConstructor
public class CreateUserRequest {
    @NotBlank private String username;
    @Email @NotBlank private String email;
    @NotBlank @Size(min = 8, max = 100) private String password;
}
```

- Fail fast: validate before any DB write.
- Validation failures already map to `400` / `ERROR_400_VALIDATION` via `handleValidationException`.

---

## Transactions

- `@Transactional` on service write methods; `@Transactional(readOnly = true)` on reads (lets the DB/Hibernate optimize, no dirty-checking).
- Keep transactions short — no remote calls, no email sending, no sleeps inside a transaction.
- Self-invocation does not start a new transaction (Spring proxy limitation). Call through the injected bean, not `this.method()`.
- Do side effects **after commit** — async via `ThreadPoolConfig`, or `TransactionSynchronization.afterCommit`.

---

## Logging

- Use SLF4J (`@Slf4j` Lombok) — never `System.out.println`.
- Levels: `error` (unexpected, with stack trace), `warn` (handled business failure worth noting), `info` (lifecycle/business events), `debug` (dev detail).
- Log the unexpected-exception stack trace in `handleGenericException` before returning the generic `500`.
- Never log secrets, full tokens, passwords, or full card/account numbers. Mask sensitive values.
- Add a correlation/request id (MDC) for tracing across logs once traffic grows.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    public WithdrawResult withdraw(...) {
        log.info("withdraw account={} amount={} ref={}", accountId, amount, referenceId);
        // ...
    }
}
```

---

## Testing

Stack: JUnit 5 + Mockito + Testcontainers (see `design/architecture.md`).

- **Unit (service):** mock repositories with Mockito. Assert business rules and the right `ErrorCode` is thrown.
- **Integration (controller/repo):** Testcontainers Postgres (not H2) for anything touching locking/`@Version` — H2 won't reproduce real lock behavior.
- **Concurrency:** N-thread test (or k6, see `k6-test/`) per money mutation asserting non-negative, exact final balance.
- Test the failure paths, not only the happy path: insufficient funds, duplicate `referenceId`, optimistic conflict, unauthorized owner.
- Name tests `methodName_condition_expectedResult` (e.g. `withdraw_insufficientFunds_throws422`).

```java
@Test
void withdraw_insufficientFunds_throwsBusinessException() {
    when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(accountWith("100.00")));
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.withdraw(1L, withdrawReq("500.00")));
    assertEquals(ErrorCode.INSUFFICIENT_FUNDS, ex.getErrorCode());
}
```

---

## Performance

- **Avoid N+1 queries.** Use `@EntityGraph` or `join fetch` for needed associations; default lazy-load relations.
- **Paginate** list endpoints — return `Page<T>`, never an unbounded `findAll()` on a growing table (transaction history already specifies `?page=&size=`).
- **Index** columns used in lookups/constraints: `username`, `email`, `account_number`, `reference_id`.
- **Projections / DTO queries** when you only need a few columns — don't load whole entities to map one field.
- **Connection pool** (HikariCP, Spring default): size it to the DB, don't over-provision.
- Don't hold a pessimistic lock longer than the mutation needs — keep the locked transaction tight.

---

## API design

- RESTful resource paths: `/api/accounts/{id}/transactions`. Plural nouns, verbs as sub-resources only when unavoidable (`/withdraw`, `/deposit` are domain operations — acceptable).
- HTTP status policy (from `design/api.md`): 400 malformed, 401 no/invalid token, 403 not owner, 404 missing, 409 state conflict, 422 business rule, 500 unexpected.
- Every response in the `ApiResponse<T>` envelope.
- Version the API if/when breaking changes arrive (`/api/v1/...`).
- Document with springdoc `@Operation` / `@ApiResponses` so Swagger stays accurate.

---

## Config & secrets

- Profiles: H2 + `ddl-auto: create-drop` for dev; Postgres + `ddl-auto: validate` + Flyway for prod.
- Schema changes via Flyway migrations in prod — never `ddl-auto: update`.
- Secrets (JWT key, DB password) from environment variables, never committed.

---

## Quick review checklist

- [ ] Constructor injection, `final` fields, no `@Autowired` fields
- [ ] DTO in / DTO out — no entity escapes as JSON
- [ ] `@Valid` on request bodies
- [ ] Business rules throw `BusinessException(ErrorCode)`
- [ ] Writes `@Transactional`, reads `readOnly = true`
- [ ] `BigDecimal` for money, `compareTo` not `equals`
- [ ] Passwords bcrypt-hashed; no secrets in code/logs
- [ ] Lists paginated; lookup columns indexed
- [ ] Tests cover failure paths + concurrency
