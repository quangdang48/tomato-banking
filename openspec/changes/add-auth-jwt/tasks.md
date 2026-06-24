## 1. Dependencies and Configuration

- [x] 1.1 Add `spring-boot-starter-security` and JJWT dependencies to `pom.xml`.
- [x] 1.2 Add `app.jwt.secret`, `app.jwt.expires-in`, and `app.jwt.issuer` config to `application.yaml`.
- [x] 1.3 Add `JwtProperties` and enable configuration-properties scanning.

## 2. DTOs and Error Model

- [x] 2.1 Add `RegisterRequest`, `LoginRequest`, and `AuthResponse` DTOs with Bean Validation.
- [x] 2.2 Add `ERROR_401_2200` and `ERROR_401_2201` to `ErrorCode`.
- [x] 2.3 Update `GlobalExceptionHandler` to map auth error codes to HTTP 401.

## 3. Authentication Services

- [x] 3.1 Add `JwtService` to generate, parse, and validate signed JWT access tokens.
- [x] 3.2 Add `AuthService` and implementation for register and login.
- [x] 3.3 Ensure every user-creation path stores BCrypt password hashes, including existing `UserServiceImpl.createUser`.
- [x] 3.4 Ensure failed login uses the same `ERROR_401_2200` response for unknown user and wrong password.

## 4. Security Wiring

- [x] 4.1 Add `JwtAuthenticationFilter` to read bearer tokens, validate them, and set `SecurityContext`.
- [x] 4.2 Add `SecurityConfig` with stateless sessions, BCrypt password encoder, public route allowlist, and protected default policy.
- [x] 4.3 Ensure missing, expired, malformed, or tampered tokens on protected routes return HTTP 401 with `ERROR_401_2201`.

## 5. Controllers and Swagger

- [x] 5.1 Add `AuthController` with `POST /api/auth/register` and `POST /api/auth/login`.
- [x] 5.2 Wrap all auth responses in `ApiResponse<T>` and return `201` for successful registration.
- [x] 5.3 Update `SwaggerConfig` with bearer JWT security scheme.

## 6. Verification

- [x] 6.1 Add unit tests for registration uniqueness, password hashing, login success, and login failure.
- [x] 6.2 Add JWT service tests for valid, expired, and tampered tokens.
- [x] 6.3 Add MockMvc integration tests for public auth routes and protected route authentication behavior.
- [x] 6.4 Run `mvn test` and fix any regressions.
