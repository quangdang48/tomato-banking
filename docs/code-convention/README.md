# Code Conventions

Rules derived from the actual codebase. New code must follow these or update the doc.

| File | Covers |
|------|--------|
| [java.md](java.md) | Naming, Lombok, DI, package layout, general Java patterns |
| [exception.md](exception.md) | `ErrorCode`, `BusinessException`, `ObjectsValidator`, `GlobalExceptionHandler` |
| [layers.md](layers.md) | Layer responsibilities — controller, service, repository, entity, DTO |
| [banking.md](banking.md) | Money correctness, transactions, concurrency, idempotency |
| [best-practices.md](best-practices.md) | General backend best practices — security, validation, logging, testing, performance |

## Non-negotiables

- Every balance mutation is `@Transactional`.
- Never expose entities directly in HTTP responses — always map to a DTO.
- All HTTP responses use the `ApiResponse<T>` envelope.
- Throw `BusinessException(ErrorCode)` from service layer; never map to HTTP status manually in a service.
- Use `BigDecimal` for all money amounts. Never `double` or `float`.
