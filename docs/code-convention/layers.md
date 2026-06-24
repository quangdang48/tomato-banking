# Layer Conventions

3-tier flow: `Controller → Service → Repository → Entity`, with DTOs at the edge and `ApiResponse` wrapping every reply. Each layer has one job; do not leak responsibilities across boundaries.

```
HTTP request
   │  @Valid DTO
   ▼
Controller      HTTP only: validate, call service, wrap in ApiResponse
   │  entity / domain args
   ▼
Service         business rules, @Transactional, throws BusinessException
   │  entity
   ▼
Repository      Spring Data JPA, queries, locking
   │
   ▼
Entity / DB
```

---

## Controller

**Job:** HTTP concerns only. No business logic, no transactions, no repository access.

Rules:
- Annotate `@RestController` + `@RequestMapping("/api/...")` + `@RequiredArgsConstructor`.
- Depend on the service **interface**, never the `Impl`.
- Validate input with `@Valid @RequestBody`.
- Map entity → response DTO via `DTO.from(entity)`.
- Wrap every result in `ApiResponse` and return `ResponseEntity` with the right status.
- Document with `@Tag` / `@Operation` / `@ApiResponses`.
- Never catch `BusinessException` — let it bubble to `GlobalExceptionHandler`.

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User management APIs")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = UserResponse.from(userService.createUser(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
```

Status conventions: `201` create, `200` read/update, `204` delete (return `ResponseEntity<Void>` with `noContent()`).

---

## Service

**Job:** business rules + transaction boundaries. The only layer that throws `BusinessException`.

Rules:
- Interface + `@Service`-annotated `*Impl` (see [java.md](java.md)).
- `@RequiredArgsConstructor` for repository injection.
- `@Transactional` on write methods; `@Transactional(readOnly = true)` on read methods.
- Validate business rules (existence, uniqueness, ownership, funds) here — not in the controller.
- Accept DTOs / ids, return **entities** (controller maps to response DTO). Do not return entities that escape as JSON.
- Idempotency, balance checks, audit-record creation all live here (see [banking.md](banking.md)).

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User createUser(CreateUserRequest request) {
        ObjectsValidator.mustNull(
            userRepository.findByUsername(request.getUsername()).orElse(null),
            ErrorCode.ERROR_409_2002);
        ObjectsValidator.mustNull(
            userRepository.findByEmail(request.getEmail()).orElse(null),
            ErrorCode.ERROR_409_2003);

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .fullName(request.getFullName())
            .passwordHash(request.getPassword())   // TODO: bcrypt before save — see best-practices.md
            .build();
        return userRepository.save(user);
    }
}
```

> Current `createUser` stores the raw password into `passwordHash`. Per [domain.md](../design/domain.md) it must be bcrypt-hashed. Treat that as a known gap, not the convention — hash in the service before `save`.

---

## Repository

**Job:** data access only. Spring Data JPA interfaces. No business logic.

Rules:
- Extend `JpaRepository<Entity, IdType>`.
- Derived query methods follow Spring Data naming: `findByUsername`, `existsByEmail`.
- Use `@Query` only when derived names get unwieldy.
- Locking lives here via `@Lock` on a finder (see [banking.md](banking.md)):

```java
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
```

---

## Entity

**Job:** map a table. JPA annotations + Lombok.

Rules:
- `@Entity` + `@Table(name = "...")` (snake_case plural).
- `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. Avoid `@Data` (breaks JPA equals/hashCode).
- `@Column(name = "snake_case")` for multi-word columns; `nullable`/`unique` constraints declared here.
- Money columns: `BigDecimal` mapped to `NUMERIC(19,4)` (see [banking.md](banking.md)).
- Optimistic locking: add `@Version` field.
- **Never serialized directly to HTTP** — always go through a DTO.

```java
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Version
    private Long version;
}
```

---

## DTO

**Job:** the HTTP contract. Decouples API shape from the DB schema.

Rules:
- **Request DTO:** record preferred for new code; Bean Validation annotations live on record components (`@NotBlank`, `@Email`, `@Positive`).
- **Response DTO:** record preferred for new code; include a static `from(Entity)` factory when mapping from an entity.
- Never expose `passwordHash` or other sensitive fields in a response DTO.
- One DTO per use case — don't reuse a request DTO as a response.

```java
// Request
public record CreateUserRequest(
    @NotBlank String username,
    @Email @NotBlank String email,
    String fullName,
    @NotBlank @Size(min = 8) String password
) {}

// Response
public record UserResponse(Integer id, String username, String email, String fullName) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName());
    }
}
```

---

## Boundary cheat-sheet

| Concern | Lives in |
|---------|----------|
| HTTP status, request parsing | Controller |
| `@Valid` structural validation | DTO (annotations) + Controller (trigger) |
| Business rules, uniqueness, funds | Service |
| `@Transactional` | Service impl method |
| Throwing `BusinessException` | Service |
| Mapping `ErrorCode → HttpStatus` | `GlobalExceptionHandler` |
| Entity ↔ DTO mapping | `DTO.from()` factory |
| Queries, locking | Repository |
| Table/column mapping | Entity |
