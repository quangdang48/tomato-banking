## Context

The project currently has a `User` entity, `UserController`, `UserService`, `ApiResponse<T>`, and centralized `BusinessException(ErrorCode)` handling. `UserServiceImpl.createUser` stores the request password directly in `passwordHash`, which must be fixed before auth can be trusted.

Phase 1 introduces stateless JWT auth for the REST API. Registration and login remain public. Future onboarding, account, and transaction endpoints rely on the authenticated principal from the JWT.

## Goals / Non-Goals

**Goals:**

- Add public `/api/auth/register` and `/api/auth/login` endpoints.
- BCrypt-hash passwords before persistence.
- Issue signed JWT access tokens with username subject, user id claim, issuer, issue time, and expiration.
- Validate `Authorization: Bearer <token>` on protected requests.
- Keep all success and error payloads wrapped in `ApiResponse<T>`.
- Add auth-specific error codes for invalid credentials and invalid/expired tokens.
- Expose JWT bearer auth in Swagger UI.

**Non-Goals:**

- Refresh tokens.
- Logout/token revocation/blacklist.
- Roles and authorities.
- Account ownership checks.
- Admin/reviewer authorization.

## Decisions

### Use Spring Security with stateless sessions

Spring Security will own request authentication and route protection. Sessions will be disabled with `SessionCreationPolicy.STATELESS` because the API uses bearer tokens, not cookies.

Alternatives considered:

- Custom servlet filter without Spring Security: less integration with `SecurityContext` and future route authorization.
- Stateful session login: not appropriate for a REST API intended to use JWT.

### Use BCrypt for password hashing

Registration MUST hash passwords using `BCryptPasswordEncoder`. Login uses `PasswordEncoder.matches`.

Alternatives considered:

- Store raw password: rejected; existing known gap and security failure.
- SHA hashing: rejected; not appropriate for password storage.

### Use JJWT with HS256 shared secret

JWT signing/parsing is isolated in `JwtService`. The secret comes from `app.jwt.secret`, with environment override. The token includes:

- `sub`: username
- `uid`: user id
- `iss`: configured issuer
- `iat`: issued-at timestamp
- `exp`: expiration timestamp

Alternatives considered:

- Asymmetric keys: stronger operational boundary but unnecessary for this portfolio phase.
- Opaque tokens in DB: enables revocation but adds persistence/state that is out of scope.

### Keep login business rules in `AuthService`

`AuthService.login` will load by username, verify password, and throw `BusinessException(ERROR_401_2200)` for both unknown user and wrong password.

Alternatives considered:

- Use full `DaoAuthenticationProvider` / `UserDetailsService`: useful later, but direct service logic better preserves the existing `BusinessException(ErrorCode)` response model in this phase.

### Register through one user-creation path

Password hashing and uniqueness rules should live in exactly one path. Either `AuthService.register` delegates to a hashing-aware `UserService.createUser`, or `AuthService.register` replaces public registration and applies the same rules itself. Avoid two separate implementations that can drift.

### Security route policy

Permit unauthenticated access to:

- `/api/auth/**`
- `/v3/api-docs/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/h2-console/**`

All other routes require authentication.

## Risks / Trade-offs

- [Risk] JWT secret is too short or committed accidentally -> Mitigation: bind from `JWT_SECRET`, document dev-only fallback, require at least 32 bytes for HS256.
- [Risk] Invalid token errors bypass `GlobalExceptionHandler` because they occur in the filter chain -> Mitigation: configure authentication entry point or filter error response so `401` uses `ERROR_401_2201` envelope.
- [Risk] Adding Spring Security locks all endpoints by default -> Mitigation: add `SecurityConfig` in the same change as dependencies.
- [Risk] Existing `/api/users` registration path may still store raw passwords -> Mitigation: update `UserServiceImpl.createUser` to hash or route all registration through `AuthService`.
- [Risk] No roles yet means all authenticated users are equivalent -> Mitigation: leave authorities empty and add roles in a later admin/onboarding phase.

## Migration Plan

1. Add dependencies and JWT config.
2. Add DTOs, `JwtProperties`, `JwtService`, `AuthService`, security filter/config, and `AuthController`.
3. Update password creation path to BCrypt-hash stored passwords.
4. Add `ERROR_401_2200` and `ERROR_401_2201`; map them to HTTP 401.
5. Update Swagger config with bearer scheme.
6. Add unit and MockMvc tests for registration, login, token validation, and protected routes.

Rollback: remove the security dependency/config/filter and auth endpoints. Existing user rows remain valid, but any BCrypt-hashed rows should not be treated as raw passwords by older code.

## Open Questions

- Should `/api/users` remain public after auth lands, or should public user creation move only to `/api/auth/register`?
- Should invalid JWT responses be emitted by a custom `AuthenticationEntryPoint` or directly by `JwtAuthenticationFilter`?
