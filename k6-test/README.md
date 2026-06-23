# k6 Concurrency Tests

Validates that concurrent withdrawals never drive balance negative under both lock strategies.

## Prerequisites

```bash
# Install k6
# macOS
brew install k6
# Linux
sudo gpg -k && sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

App must be running on `http://localhost:8088`.

## Tests

| File | Strategy | What it proves |
|------|----------|----------------|
| `optimistic-lock.test.js` | `@Version` / HTTP 409 | Conflicting writers rejected, balance stays correct |
| `pessimistic-lock.test.js` | `SELECT … FOR UPDATE` | Writers serialized, no negative balance |

## Run

```bash
# Optimistic lock — 20 VUs hammer same account for 10s
k6 run k6-test/optimistic-lock.test.js

# Pessimistic lock — 20 VUs, writes serialized
k6 run k6-test/pessimistic-lock.test.js

# Override VU count / duration
k6 run --vus 50 --duration 30s k6-test/optimistic-lock.test.js

# Output summary as JSON (for CI)
k6 run --out json=results.json k6-test/optimistic-lock.test.js
```

## What each test does

### Setup phase (runs once, single VU)

1. Register a dedicated test user (`test_concurrent_<timestamp>`).
2. Login → get JWT.
3. Create an account with a known initial balance (default **10 000**).
4. Pass `{ token, accountId, initialBalance }` to all VUs.

### Default phase (all VUs in parallel)

Each VU iteration:
- Sends `POST /api/accounts/{id}/withdraw` with a **unique `referenceId`** (`vu-{VU}-iter-{ITER}`).
- Asserts response is one of the *expected* statuses for that strategy (see below).

### Teardown / final check

Fetches the final balance and asserts:
- `finalBalance >= 0` — never negative.
- `finalBalance` matches `initialBalance − (successCount × withdrawAmount)` within rounding tolerance.

## Expected responses per strategy

### Optimistic lock (`optimistic-lock.test.js`)

| HTTP | Meaning | Counted as |
|------|---------|-----------|
| 200 | Withdraw succeeded | `success` |
| 409 + code 2103 | Optimistic lock conflict — client should retry | `conflict` (expected) |
| 422 + code 2100 | Insufficient funds (balance hit zero) | `exhausted` (expected) |
| anything else | Unexpected — **test fails** | `error` |

### Pessimistic lock (`pessimistic-lock.test.js`)

| HTTP | Meaning | Counted as |
|------|---------|-----------|
| 200 | Withdraw succeeded | `success` |
| 422 + code 2100 | Insufficient funds (balance exhausted) | `exhausted` (expected) |
| 409 + code 2102 | Duplicate referenceId (idempotency replay) | `duplicate` (benign) |
| anything else | Unexpected — **test fails** | `error` |

> With pessimistic locking, **no 409/2103 conflicts** should appear — writers serialize on the DB row.

## Thresholds (built into scripts)

```
http_req_failed           < 1%    (5xx = bug)
checks                    > 95%   (expected statuses)
http_req_duration p(95)   < 2000ms
```

Adjust in each script's `options.thresholds` block.

## CI integration

```yaml
# Example GitHub Actions step
- name: Run k6 concurrency tests
  run: |
    k6 run --out json=k6-optimistic.json k6-test/optimistic-lock.test.js
    k6 run --out json=k6-pessimistic.json k6-test/pessimistic-lock.test.js
```

Exit code is non-zero when any threshold breaches — CI fails automatically.
