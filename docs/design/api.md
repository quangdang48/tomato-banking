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
| POST | `/api/onboarding/profile` | yes | Create onboarding profile |
| GET | `/api/onboarding/status` | yes | Get onboarding status |
| PUT | `/api/onboarding/kyc` | yes | Create/update individual KYC |
| PUT | `/api/onboarding/kyb` | yes | Create/update business KYB |
| POST | `/api/onboarding/beneficial-owners` | yes | Add KYB beneficial owner |
| PUT | `/api/onboarding/beneficial-owners/{id}` | yes | Update KYB beneficial owner |
| DELETE | `/api/onboarding/beneficial-owners/{id}` | yes | Remove KYB beneficial owner before submit |
| POST | `/api/onboarding/documents` | yes | Add verification document metadata |
| POST | `/api/onboarding/submit` | yes | Submit onboarding for review |
| GET | `/api/accounts/{id}/balance` | yes | Get balance |
| POST | `/api/accounts/{id}/deposit` | yes | Deposit money |
| POST | `/api/accounts/{id}/withdraw` | yes | Withdraw money |
| GET | `/api/accounts/{id}/transactions` | yes | Transaction history |

> User CRUD already exists via `UserController`. Auth, onboarding, and account endpoints
> are planned per roadmap.

Delivery phases:

- Phase 1: auth + individual eKYC endpoints (`/api/auth/**`, `/api/onboarding/profile`,
  `/api/onboarding/kyc`, `/api/onboarding/documents`, `/api/onboarding/submit`).
- Phase 2: business KYB endpoints (`/api/onboarding/kyb`,
  `/api/onboarding/beneficial-owners/**`).
- Phase 3: banking core endpoints (`/api/accounts/**`).

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

## Onboarding

Onboarding must be `APPROVED` before the user can create active accounts or perform money
movement.

### POST /api/onboarding/profile
```json
// request
{ "customerType": "INDIVIDUAL" }
// response.data
{ "profileId": 10, "customerType": "INDIVIDUAL", "status": "DRAFT" }
```

### GET /api/onboarding/status
```json
// response.data
{
  "profileId": 10,
  "customerType": "INDIVIDUAL",
  "status": "SUBMITTED",
  "submittedAt": "2026-06-24T09:30:00Z",
  "reviewedAt": null
}
```

### PUT /api/onboarding/kyc
```json
// request
{
  "legalName": "Tom A",
  "dateOfBirth": "1995-01-20",
  "documentType": "CCCD",
  "documentNumber": "012345678901",
  "streetAddress": "1 Tomato Street",
  "district": "District 1",
  "provinceCity": "Ho Chi Minh City"
}
// response.data
{ "profileId": 10, "status": "DRAFT" }
```

### PUT /api/onboarding/kyb
```json
// request
{
  "legalBusinessName": "Tomato Trading Co",
  "registrationNumber": "0312345678",
  "taxId": "0312345678",
  "incorporationDate": "2020-01-15",
  "businessAddressLine1": "1 Tomato Street",
  "district": "District 1",
  "provinceCity": "Ho Chi Minh City",
  "industry": "FINTECH"
}
// response.data
{ "profileId": 10, "status": "DRAFT" }
```

### POST /api/onboarding/beneficial-owners
```json
// request
{
  "legalName": "Owner A",
  "dateOfBirth": "1990-03-10",
  "ownershipPercentage": 50.00,
  "documentType": "CCCD",
  "documentNumber": "012345678902"
}
// response.data
{ "ownerId": 100, "legalName": "Owner A", "ownershipPercentage": 50.00 }
```

### POST /api/onboarding/documents
```json
// request - metadata only; file storage is handled outside this phase
{
  "documentType": "IDENTITY_FRONT",
  "storageKey": "kyc/10/identity-front.png",
  "originalFilename": "identity-front.png",
  "contentType": "image/png",
  "sizeBytes": 250000
}
// response.data
{ "documentId": 500, "documentType": "IDENTITY_FRONT", "status": "UPLOADED" }
```

### POST /api/onboarding/submit
```json
// response.data
{ "profileId": 10, "customerType": "INDIVIDUAL", "status": "SUBMITTED" }
```

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
- Onboarding profile must be `APPROVED` before active account creation or money movement.
- KYC requires legal identity, Vietnam address fields, and at least one identity document metadata row.
- KYB requires business identity, Vietnam address fields, at least one beneficial owner, and at least one
  business document metadata row.

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

### Codes to add for onboarding
| HTTP | code (suggested) | Meaning |
|------|------------------|---------|
| 404 | 2300 | Onboarding profile not found |
| 409 | 2301 | Onboarding profile already exists |
| 403 | 2302 | Onboarding is not approved |
| 400 | 2303 | Required KYC data is missing |
| 400 | 2304 | Required KYB data is missing |
| 400 | 2305 | Required document is missing |
| 409 | 2306 | Invalid onboarding status transition |
| 400 | 2307 | Beneficial owner is required |
| 409 | 2308 | Onboarding has been rejected |

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
