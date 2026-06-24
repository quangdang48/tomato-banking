# Exception & Error Handling

One error model across the app: services throw `BusinessException(ErrorCode)`; `GlobalExceptionHandler` maps it to an HTTP status + `ApiResponse` envelope. Controllers never build error responses by hand.

```
service throws BusinessException(ErrorCode)
        │
        ▼
GlobalExceptionHandler  (@RestControllerAdvice)
        │  maps ErrorCode → HttpStatus
        ▼
ApiResponse.error(errorCode, message)   →   { code, message, data: null }
```

---

## 1. `ErrorCode` enum

Single source of truth for app error codes and default messages. `code` is the numeric value returned in the `ApiResponse.code` field (0 = success).

```java
public enum ErrorCode {
    SUCCESS(0, "Success"),
    ERROR_400_4000(4000, "Bad request"),
    ERROR_400_VALIDATION(4001, "Validation failed"),
    ERROR_404_2001(2001, "User not found"),
    ERROR_409_2002(2002, "Username already taken"),
    ERROR_409_2003(2003, "Email already in use"),
    ERROR_500_5000(5000, "Internal server error");
    // ...
}
```

### Naming convention

`ERROR_{HTTP}_{CODE}` — the HTTP status and the numeric code are both encoded in the name so the mapping is readable at the call site.

> Note: `ERROR_400_VALIDATION` and `ERROR_500_5000` use a label suffix instead of the bare code. Keep new entries consistent: prefer `ERROR_{HTTP}_{CODE}`; a descriptive suffix is acceptable only for the framework-level catch-alls (validation, internal).

### Adding a new code

1. Add the entry to `ErrorCode` with a unique numeric code (banking codes start at `2100` — see [banking.md](banking.md) and `docs/design/api.md`).
2. Add the `ErrorCode → HttpStatus` branch in `GlobalExceptionHandler` if it is not a `400`.
3. Document the row in `docs/api.md` error table.

Planned banking codes (per `docs/api.md`):

| code | Meaning | HTTP |
|------|---------|------|
| 2100 | Insufficient funds | 422 |
| 2101 | Amount must be > 0 | 400 |
| 2102 | Duplicate referenceId (idempotency replay) | 409 |
| 2103 | Optimistic lock conflict — retry | 409 |
| 2104 | Account not owned by caller | 403 |
| 2105 | Account not ACTIVE | 409 |

Planned onboarding codes:

| code | Meaning | HTTP |
|------|---------|------|
| 2300 | Onboarding profile not found | 404 |
| 2301 | Onboarding profile already exists | 409 |
| 2302 | Onboarding is not approved | 403 |
| 2303 | Required KYC data is missing | 400 |
| 2304 | Required KYB data is missing | 400 |
| 2305 | Required document is missing | 400 |
| 2306 | Invalid onboarding status transition | 409 |
| 2307 | Beneficial owner is required | 400 |
| 2308 | Onboarding has been rejected | 409 |

---

## 2. `BusinessException`

A `RuntimeException` (unchecked — no `throws` clauses, rolls back `@Transactional` by default) carrying an `ErrorCode`. Two constructors: default message from the enum, or a custom message.

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
```

Throw it from the **service layer** for any business-rule violation. Never throw it from a controller, and never catch it just to remap status — that is the handler's job.

```java
// Good
if (balance.compareTo(amount) < 0) {
    throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);
}

// Custom message (e.g. include the account id)
throw new BusinessException(ErrorCode.ERROR_404_2001, "Account " + id + " not found");
```

---

## 3. `ObjectsValidator`

Static guards for the most common null/uniqueness checks. Use it to keep services declarative.

```java
ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);              // throws if null
ObjectsValidator.mustNull(existing, ErrorCode.ERROR_409_2002);            // throws if NOT null (uniqueness)
ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001, "User " + id + " missing");
```

Pattern in practice:

```java
// existence check
User user = userRepository.findById(id).orElse(null);
ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);

// uniqueness check
ObjectsValidator.mustNull(
    userRepository.findByEmail(request.getEmail()).orElse(null),
    ErrorCode.ERROR_409_2003
);
```

Equivalent `Optional` style is also acceptable for existence — pick one per method and be consistent:

```java
User user = userRepository.findById(id)
    .orElseThrow(() -> new BusinessException(ErrorCode.ERROR_404_2001));
```

---

## 4. `GlobalExceptionHandler`

`@RestControllerAdvice` with one handler per exception family. Three handlers today:

| Handler | Catches | Result |
|---------|---------|--------|
| `handleBusinessException` | `BusinessException` | maps `ErrorCode → HttpStatus`, returns envelope |
| `handleValidationException` | `MethodArgumentNotValidException` | joins field errors → `400` + `ERROR_400_VALIDATION` |
| `handleGenericException` | `Exception` | `500` + `ERROR_500_5000`, generic message (no internals leaked) |

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
    ErrorCode errorCode = ex.getErrorCode();
    HttpStatus status;
    if (errorCode == ErrorCode.ERROR_404_2001) {
        status = HttpStatus.NOT_FOUND;
    } else if (errorCode == ErrorCode.ERROR_409_2002 || errorCode == ErrorCode.ERROR_409_2003) {
        status = HttpStatus.CONFLICT;
    } else {
        status = HttpStatus.BAD_REQUEST;
    }
    return new ResponseEntity<>(ApiResponse.error(errorCode, ex.getMessage()), status);
}
```

### Rules

- **Never leak internals.** The generic handler returns a fixed message; the real exception is logged, not returned. Log it with `log.error("Unhandled", ex)` before responding.
- **Extend the status mapping** when you add a code that is not `400`. The `if/else` chain is the only place HTTP status is decided.
- As the chain grows (banking adds 403/409/422), consider moving the `ErrorCode → HttpStatus` link onto the enum itself (an `HttpStatus httpStatus` field per entry) so the handler becomes `return ResponseEntity.status(errorCode.getHttpStatus())...`. Recommended once codes exceed ~10.

---

## 5. Envelope — `ApiResponse<T>`

Every response, success or error, uses this shape. `@JsonInclude(NON_NULL)` drops `data` on errors.

```java
ApiResponse.success(data);                       // { "code": 0, "message": "Success", "data": {...} }
ApiResponse.error(errorCode);                    // { "code": 2001, "message": "User not found" }
ApiResponse.error(errorCode, "custom message");
```

Controllers wrap the success path; the handler wraps the error path. A controller never returns a raw entity or a bare DTO.

---

## Do / Don't

| Do | Don't |
|----|-------|
| Throw `BusinessException` in services | `try/catch` + manual `ResponseEntity` in controllers |
| Add new failures as `ErrorCode` entries | Throw raw `RuntimeException` with string messages |
| Let `GlobalExceptionHandler` own status mapping | Map `ErrorCode → HttpStatus` outside the handler |
| Log full stack trace for `500`s | Return exception message / stack to the client |
| Use `ObjectsValidator` for null/uniqueness | Scatter `if (x == null) throw` everywhere |
