# API

Base URL: `http://localhost:8088`
Swagger UI: `/swagger-ui.html` · OpenAPI: `/v3/api-docs`

All responses use the `ApiResponse` envelope:

```json
{
  "code": 0,
  "message": "Success",
  "data": { }
}
```

`code` follows `ErrorCode` (`com.dumy.exception.ErrorCode`); `0` = success.

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | no | Create user |
| POST | `/api/auth/login` | no | Login, returns JWT |
| GET | `/api/accounts/{id}/balance` | yes | Get balance |
| POST | `/api/accounts/{id}/deposit` | yes | Deposit money |
| POST | `/api/accounts/{id}/withdraw` | yes | Withdraw money |
| GET | `/api/accounts/{id}/transactions` | yes | Transaction history |

> User CRUD already exists via `UserController`. Auth + account endpoints are planned per roadmap.

## Auth

### POST /api/auth/register
```json
// request
{ "username": "tomato", "email": "t@x.com", "fullName": "Tom A", "password": "secret123" }
// response.data
{ "id": 1, "username": "tomato", "email": "t@x.com", "fullName": "Tom A" }
```

### POST /api/auth/login
```json
// request
{ "username": "tomato", "password": "secret123" }
// response.data
{ "token": "eyJhbGci...", "expiresIn": 3600 }
```
Send token on protected calls: `Authorization: Bearer <token>`.

## Accounts

### GET /api/accounts/{id}/balance
```json
// response.data
{ "accountId": 10, "accountNumber": "ACC-0001", "balance": 1500.0000, "currency": "VND" }
```

### POST /api/accounts/{id}/deposit
```json
// request — referenceId is the idempotency key
{ "amount": 500.00, "referenceId": "dep-2026-06-21-abc123" }
// response.data
{ "transactionId": 55, "type": "DEPOSIT", "amount": 500.0000, "balanceAfter": 2000.0000, "status": "COMPLETED" }
```

### POST /api/accounts/{id}/withdraw
```json
// request
{ "amount": 300.00, "referenceId": "wd-2026-06-21-def456" }
// response.data
{ "transactionId": 56, "type": "WITHDRAW", "amount": 300.0000, "balanceAfter": 1700.0000, "status": "COMPLETED" }
```

### GET /api/accounts/{id}/transactions?page=0&size=20
```json
// response.data
{
  "content": [
    { "transactionId": 56, "type": "WITHDRAW", "amount": 300.0000, "balanceAfter": 1700.0000, "createdAt": "2026-06-21T10:00:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 42
}
```

## Validation rules

- `amount` > 0 (reject zero / negative).
- Withdraw `amount` <= current balance.
- `referenceId` required + unique per account (idempotency).
- `username` / `email` unique on register.
- `currency` ISO 4217 (3 letters).

## Error model

`BusinessException(ErrorCode)` → mapped by `GlobalExceptionHandler` to HTTP status + envelope.

| HTTP | code | ErrorCode | Meaning |
|------|------|-----------|---------|
| 200 | 0 | SUCCESS | OK |
| 400 | 4000 | ERROR_400_4000 | Bad request |
| 400 | 4001 | ERROR_400_VALIDATION | Bean validation failed |
| 404 | 2001 | ERROR_404_2001 | User (or account) not found |
| 409 | 2002 | ERROR_409_2002 | Username already taken |
| 409 | 2003 | ERROR_409_2003 | Email already in use |
| 500 | 5000 | ERROR_500_5000 | Internal error |

### Codes to add for banking
| HTTP | code (suggested) | Meaning |
|------|------------------|---------|
| 422 | 2100 | Insufficient funds |
| 400 | 2101 | Amount must be > 0 |
| 409 | 2102 | Duplicate referenceId (idempotency replay) |
| 409 | 2103 | Optimistic lock conflict — retry |
| 403 | 2104 | Account not owned by caller |
| 409 | 2105 | Account not ACTIVE (frozen/closed) |

Error response shape:
```json
{ "code": 2100, "message": "Insufficient funds", "data": null }
```

## Status code policy

- **400** malformed / invalid input
- **401** missing / invalid token
- **403** authenticated but not the owner
- **404** resource missing
- **409** state conflict (duplicate, lock, account status)
- **422** business rule violation (insufficient funds)
- **500** unexpected
