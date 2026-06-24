# auth-jwt Specification

## Purpose
TBD - created by archiving change add-auth-jwt. Update Purpose after archive.
## Requirements
### Requirement: Public user registration
The system SHALL expose `POST /api/auth/register` as a public endpoint that creates a user and returns the created user DTO in the `ApiResponse` envelope.

#### Scenario: Successful registration
- **WHEN** a request provides a unique username, unique email, optional full name, and valid password
- **THEN** the system creates the user, stores a BCrypt password hash, and returns HTTP 201 with `ApiResponse.data` containing `id`, `username`, `email`, and `fullName`

#### Scenario: Duplicate username
- **WHEN** a registration request uses an existing username
- **THEN** the system returns HTTP 409 with `ERROR_409_2002`

#### Scenario: Duplicate email
- **WHEN** a registration request uses an existing email
- **THEN** the system returns HTTP 409 with `ERROR_409_2003`

#### Scenario: Password is not exposed
- **WHEN** registration succeeds
- **THEN** the response MUST NOT include `password`, `passwordHash`, or any password-derived value

### Requirement: Passwords are securely stored
The system SHALL store user passwords only as BCrypt hashes.

#### Scenario: Registration persists hash
- **WHEN** a user registers with password `secret123`
- **THEN** the `users.password_hash` value is a BCrypt hash and is not equal to `secret123`

#### Scenario: Existing user creation path is safe
- **WHEN** a user is created through any application registration/user-creation path
- **THEN** the stored `password_hash` value is BCrypt-hashed before persistence

### Requirement: User login issues JWT
The system SHALL expose `POST /api/auth/login` as a public endpoint that verifies credentials and returns a signed JWT access token in the `ApiResponse` envelope.

#### Scenario: Successful login
- **WHEN** a request provides a valid username and password
- **THEN** the system returns HTTP 200 with `ApiResponse.data.token` and `ApiResponse.data.expiresIn`

#### Scenario: Unknown username
- **WHEN** a login request provides a username that does not exist
- **THEN** the system returns HTTP 401 with `ERROR_401_2200`

#### Scenario: Wrong password
- **WHEN** a login request provides an existing username with the wrong password
- **THEN** the system returns HTTP 401 with `ERROR_401_2200`

#### Scenario: Login does not reveal user existence
- **WHEN** login fails for an unknown username or wrong password
- **THEN** both failures return the same HTTP status, error code, and message

### Requirement: JWT token contents and validation
The system SHALL sign JWT access tokens and validate their signature and expiration before accepting them.

#### Scenario: Token contains required claims
- **WHEN** login succeeds
- **THEN** the JWT contains issuer, subject username, user id claim, issued-at timestamp, and expiration timestamp

#### Scenario: Tampered token rejected
- **WHEN** a protected request uses a token whose signature or content has been tampered with
- **THEN** the system returns HTTP 401 with `ERROR_401_2201`

#### Scenario: Expired token rejected
- **WHEN** a protected request uses an expired token
- **THEN** the system returns HTTP 401 with `ERROR_401_2201`

### Requirement: Protected endpoint enforcement
The system SHALL allow public auth/documentation routes without a token and require valid bearer JWT authentication for all other routes.

#### Scenario: Public auth routes allowed
- **WHEN** a request calls `/api/auth/register` or `/api/auth/login` without an `Authorization` header
- **THEN** the request is processed without authentication

#### Scenario: Swagger and H2 console routes allowed
- **WHEN** a request calls Swagger/OpenAPI or H2 console routes without an `Authorization` header
- **THEN** the request is allowed for local development access

#### Scenario: Missing token rejected on protected route
- **WHEN** a request calls a protected route without an `Authorization: Bearer` token
- **THEN** the system returns HTTP 401 with `ERROR_401_2201`

#### Scenario: Valid token accepted on protected route
- **WHEN** a request calls a protected route with a valid bearer JWT
- **THEN** the request is authenticated and may proceed to controller handling

### Requirement: Swagger bearer authentication
The system SHALL expose an OpenAPI bearer JWT security scheme so Swagger UI can authorize protected API calls.

#### Scenario: Swagger shows bearer authorization
- **WHEN** a user opens `/swagger-ui.html`
- **THEN** Swagger UI exposes an authorization option for `Authorization: Bearer <token>`

