# Testing Conventions

JUnit tests should be easy to scan in the IDE and readable in reports.

## Test Method Naming

Use Java camelCase with underscore-separated scenario parts:

```java
methodUnderTest_WithCondition_ExpectedOutcome
```

Examples:

```java
withdraw_WithSufficientBalance_UpdatesAccount()
login_WithWrongPassword_ThrowsSameInvalidCredentialsError()
getUsers_WithoutToken_ReturnsUnauthorizedEnvelope()
```

Rules:
- Start with the method, endpoint action, or behavior under test.
- Use `With...` for the important condition or input state.
- End with the expected result.
- Keep the name specific enough to explain the assertion without reading the body.

## Display Names

Add `@DisplayName` for a human-readable sentence in test reports:

```java
@Test
@DisplayName("Should update account when balance is sufficient")
void withdraw_WithSufficientBalance_UpdatesAccount() {
    // ...
}
```

Use `Should ... when ...` phrasing for display names. The method name remains the searchable,
structured test identifier.

## Test Structure

Prefer the Arrange / Act / Assert shape without noisy comments:

```java
@Test
@DisplayName("Should return a JWT token when credentials are valid")
void login_WithValidCredentials_ReturnsToken() {
    LoginRequest request = new LoginRequest("tomato", "secret123");
    // arrange collaborators

    AuthResponse response = authService.login(request);

    assertThat(response.token()).isEqualTo("jwt-token");
}
```

When testing error paths, assert the domain `ErrorCode` whenever a `BusinessException` is expected.
