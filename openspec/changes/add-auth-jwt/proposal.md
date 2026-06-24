## Why

Tomato Banking needs a real authentication layer before onboarding, account ownership, and money movement can be safely exposed. The current user creation flow has a known password-storage gap, and protected endpoints need a stateless JWT identity model.

## What Changes

- Add public registration and login endpoints under `/api/auth`.
- Store passwords as BCrypt hashes and never persist or expose plaintext passwords.
- Issue signed JWT access tokens on successful login.
- Validate bearer tokens on protected requests and populate Spring Security context.
- Return auth failures through the existing `ApiResponse<T>` envelope and `BusinessException(ErrorCode)` model.
- Keep Swagger/OpenAPI and H2 console accessible without auth for local development.
- Add OpenAPI bearer auth metadata so Swagger UI can authorize protected calls.

## Capabilities

### New Capabilities

- `auth-jwt`: User registration, credential login, JWT token issue/validation, and protected endpoint enforcement.

### Modified Capabilities

- None.

## Impact

- Adds Spring Security and JJWT dependencies.
- Adds JWT configuration under `app.jwt`.
- Adds auth DTOs, auth service, JWT service, security filter/config, and auth controller.
- Updates `ErrorCode` and `GlobalExceptionHandler` for 401 auth errors.
- Updates Swagger config to include bearer JWT security scheme.
- Updates user creation behavior so `password_hash` stores a BCrypt hash.
