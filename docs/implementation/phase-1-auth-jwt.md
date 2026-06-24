# Phase 1 — Authentication (JWT)

Implements roadmap item **5 (Auth + per-user endpoint protection)**. Stateless JWT
auth on top of the existing `User` entity: register hashes the password, login issues a
signed token, a filter validates the token on each request and populates the
`SecurityContext`.

> Scope: registration, login, token issue/validate, request filter, security wiring,
> error codes. Refresh tokens and per-account ownership checks are **out of scope** here
> (ownership lands with the account endpoints in a later phase).

Conventions this follows: [layers.md](../code-convention/layers.md),
[exception.md](../code-convention/exception.md),
[best-practices.md](../code-convention/best-practices.md). API contract:
[design/api.md](../design/api.md).

---

## 0. Known gap to close

`UserServiceImpl.createUser` currently stores the **raw** password into `passwordHash`.
This phase fixes that — registration must bcrypt-hash before `save`. No plaintext password
may ever reach the DB or a log.

---

## 1. Dependencies (`pom.xml`)

```xml
<!-- Spring Security: filter chain, SecurityContext, BCryptPasswordEncoder -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JJWT: token build + parse (runtime impl split per JJWT 0.12.x) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

> Adding `spring-boot-starter-security` locks **every** endpoint by default. Until the
> `SecurityConfig` in §6 is in place the whole API returns `401` — add config in the same
> change.

---

## 2. Config (`application.yaml`)

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:change-me-dev-only-min-32-bytes-long-secret}
    expires-in: 3600        # seconds (matches design/api.md expiresIn)
    issuer: tomato-banking
```

- Secret from env (`JWT_SECRET`), placeholder default for dev only — never commit a real
  secret. HS256 needs **≥ 32 bytes**.
- Bind with a typed `@ConfigurationProperties` record:

```java
package com.tomato.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expiresIn, String issuer) {}
```

Enable scanning: add `@ConfigurationPropertiesScan` to `Main` (or `@EnableConfigurationProperties(JwtProperties.class)`).

---

## 3. Error codes (`ErrorCode`)

Add auth codes. Keep the `ERROR_{HTTP}_{CODE}` convention; auth block starts at `2200`
(banking reserves `2100`–`2105`, per [exception.md](../code-convention/exception.md)).

| code | name | HTTP | Meaning |
|------|------|------|---------|
| 2200 | `ERROR_401_2200` | 401 | Invalid credentials (login) |
| 2201 | `ERROR_401_2201` | 401 | Missing / invalid / expired token |

```java
ERROR_401_2200(2200, "Invalid username or password"),
ERROR_401_2201(2201, "Invalid or expired token"),
```

> Login returns `401` for **both** unknown username and bad password — same message, so the
> API never reveals which usernames exist (user-enumeration defense).

Extend the `GlobalExceptionHandler` status mapping (the `if/else` chain is the only place
HTTP status is decided):

```java
} else if (errorCode == ErrorCode.ERROR_401_2200 || errorCode == ErrorCode.ERROR_401_2201) {
    status = HttpStatus.UNAUTHORIZED;
}
```

> The handler chain now spans 400/401/404/409. Per exception.md, once codes exceed ~10
> move the `ErrorCode → HttpStatus` link onto the enum. Note this as a follow-up, not part
> of this phase.

---

## 4. DTOs

Per [layers.md](../code-convention/layers.md): requests `@Getter @NoArgsConstructor` with
Bean Validation; responses `@Getter @Builder` immutable with a static factory.

```java
// dto/RegisterRequest.java   (POST /api/auth/register)
@Getter
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank private String username;
    @Email @NotBlank private String email;
    private String fullName;
    @NotBlank @Size(min = 8, max = 100) private String password;
}

// dto/LoginRequest.java      (POST /api/auth/login)
@Getter
@NoArgsConstructor
public class LoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}

// dto/AuthResponse.java      (login response.data)
@Getter
@Builder
public class AuthResponse {
    private final String token;
    private final long expiresIn;

    public static AuthResponse of(String token, long expiresIn) {
        return AuthResponse.builder().token(token).expiresIn(expiresIn).build();
    }
}
```

Register reuses the existing `UserResponse.from(User)` for its body (no `passwordHash`
ever in a response DTO).

---

## 5. JWT token service

Owns sign + parse. No business rules — used by `AuthService` (issue) and the filter
(validate).

```java
package com.tomato.service;

import com.tomato.config.JwtProperties;
import com.tomato.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.expiresIn())))
                .signWith(key)
                .compact();
    }

    /** Parse + verify signature/expiry. Returns claims, or throws on any failure. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;   // bad signature, expired, malformed
        }
    }
}
```

---

## 6. Security config

Bean wiring: password encoder, stateless session policy, public vs protected routes,
filter registration.

```java
package com.tomato.config;

import com.tomato.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration
                    .AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                 // stateless API, no cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/h2-console/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))   // H2 console renders in a frame
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

> Auth uses the service directly (§7) rather than a `DaoAuthenticationProvider` /
> `UserDetailsService`, so login control stays in `AuthService` and emits our
> `BusinessException` error model. The `AuthenticationManager` bean is exposed for when a
> later phase wants the standard provider chain.

---

## 7. Auth service

Business layer: register (hash + persist via existing user logic) and login (verify
password, issue token). Throws `BusinessException` per
[exception.md](../code-convention/exception.md).

```java
package com.tomato.service;

import com.tomato.dto.AuthResponse;
import com.tomato.dto.LoginRequest;
import com.tomato.dto.RegisterRequest;
import com.tomato.entity.User;
import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import com.tomato.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;   // for expiresIn echo

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.ERROR_409_2002);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.ERROR_409_2003);
        }
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))   // bcrypt
                .build();
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        // Same error whether user missing or password wrong — no user enumeration.
        if (user == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ERROR_401_2200);
        }
        String token = jwtService.generate(user);
        return AuthResponse.of(token, jwtProperties.expiresIn());
    }
}
```

> `register` duplicates the user-creation rules now living in `UserServiceImpl`. Either
> have `AuthService` call `UserService.createUser` (once that hashes), or keep the
> uniqueness checks here. Pick one to avoid two copies of the rule — recommended: register
> goes through `UserService` so password-hashing lives in exactly one place.

---

## 8. JWT authentication filter

`OncePerRequestFilter`: pull the bearer token, validate, set the `SecurityContext`. No
token → chain continues unauthenticated and the `authorizeHttpRequests` rule rejects
protected routes with `401`.

```java
package com.tomato.security;

import com.tomato.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                Claims claims = jwtService.parse(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(),        // principal = username
                        null,
                        List.of());                 // no roles in phase 1
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
```

> The principal is the **username** from the verified token — `uid` claim is available via
> `claims.get("uid")` for ownership checks when account endpoints arrive. Never trust an id
> from the request body for identity.

---

## 9. Controller

HTTP only: validate, call service, wrap in `ApiResponse`. Never catches
`BusinessException`.

```java
package com.tomato.controller;

import com.tomato.common.ApiResponse;
import com.tomato.dto.AuthResponse;
import com.tomato.dto.LoginRequest;
import com.tomato.dto.RegisterRequest;
import com.tomato.dto.UserResponse;
import com.tomato.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration & JWT login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse body = UserResponse.from(authService.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    @PostMapping("/login")
    @Operation(summary = "Login, returns a JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }
}
```

---

## 10. Swagger — show the bearer scheme

So `/swagger-ui.html` gets an **Authorize** button. Add to `SwaggerConfig.openAPI()`:

```java
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

return new OpenAPI()
    .info(new Info().title("Tomato API").description("Tomato API documentation").version("1.0.0"))
    .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
    .components(new Components().addSecuritySchemes("bearerAuth",
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")));
```

---

## 11. New / changed files

```
pom.xml                                         + security + jjwt deps
src/main/resources/application.yaml             + app.jwt.*
com.tomato.Main                                 + @ConfigurationPropertiesScan
com.tomato.config.JwtProperties                 NEW  typed config
com.tomato.config.SecurityConfig                NEW  filter chain, encoder, manager
com.tomato.config.SwaggerConfig                 ~    bearer security scheme
com.tomato.security.JwtAuthenticationFilter     NEW  per-request token validation
com.tomato.service.JwtService                   NEW  sign / parse / verify
com.tomato.service.AuthService (+Impl)          NEW  register + login
com.tomato.controller.AuthController            NEW  /api/auth/register, /login
com.tomato.dto.RegisterRequest                  NEW
com.tomato.dto.LoginRequest                     NEW
com.tomato.dto.AuthResponse                     NEW
com.tomato.exception.ErrorCode                  ~    + 2200, 2201
com.tomato.exception.GlobalExceptionHandler     ~    + 401 mapping
com.tomato.service.UserServiceImpl              ~    bcrypt-hash password (close gap §0)
```

---

## 12. Acceptance checklist

- [ ] `register` stores a **bcrypt** hash; raw password never persisted or logged.
- [ ] `login` with good creds returns `{ token, expiresIn }` in the `ApiResponse` envelope.
- [ ] `login` with wrong password **or** unknown user → `401` / code `2200`, identical message.
- [ ] Protected route without token → `401`; with valid token → passes; expired/tampered → `401` / `2201`.
- [ ] `/api/auth/**`, Swagger, H2 console reachable without a token.
- [ ] JWT secret read from env; no secret committed.
- [ ] Swagger shows the **Authorize** (bearer) button.

---

## 13. Tests (per best-practices.md)

Name `methodName_condition_expectedResult`. Cover failure paths, not only happy:

- `register_duplicateUsername_throws409`
- `register_persistsBcryptHash_notRawPassword`
- `login_wrongPassword_throws401`
- `login_unknownUser_throws401` (same error as wrong password)
- `login_validCredentials_returnsToken`
- `jwtService_expiredToken_isValidFalse`
- `protectedEndpoint_noToken_returns401` (MockMvc integration)
- `protectedEndpoint_validToken_returns200`

---

## 14. Out of scope (later phases)

- **Account ownership (403 / `2104`)** — lands with account endpoints; use the `uid` claim.
- **Refresh tokens** — short-TTL access token only for now.
- **Roles / authorities** — filter sets an empty authority list; add when an admin role exists.
- **Token revocation / blacklist** — stateless tokens expire by TTL only.
