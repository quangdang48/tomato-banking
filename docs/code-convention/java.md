# Java Conventions

General Java style for this codebase. For layer-specific rules see [layers.md](layers.md); for errors see [exception.md](exception.md).

## Package layout

```
com.tomato
├── common/        ApiResponse (shared across all modules)
├── config/        Cross-module Spring config beans
├── exception/     ErrorCode, BusinessException, GlobalExceptionHandler, ObjectsValidator
└── modules/
    └── {module}/
        ├── controller/  REST endpoints
        ├── service/     Business logic — interface + *Impl
        ├── repository/  Spring Data JPA interfaces
        ├── entity/      JPA entities
        └── dto/
            ├── request/
            └── response/
```

Dependency direction is one-way inside each module: `controller → service → repository → entity`. DTO ↔ entity mapping lives in the response DTO (`from(entity)` factory) or service. **Entities never depend on DTOs.** Cross-module dependencies should go through another module's service interface unless a lower-level dependency is explicitly part of the design.

---

## Naming

| Thing                 | Convention                 | Example                      |
| --------------------- | -------------------------- | ---------------------------- |
| Class                 | PascalCase                 | `UserServiceImpl`            |
| Interface             | PascalCase, no `I` prefix  | `UserService`                |
| Impl class            | `{Interface}Impl`          | `UserServiceImpl`            |
| Method                | camelCase, verb-first      | `getUserById`, `createUser`  |
| Variable / field      | camelCase                  | `passwordHash`               |
| Constant / enum entry | UPPER_SNAKE                | `ERROR_404_2001`             |
| DB table              | `tbl_` prefix, snake_case, plural | `tbl_users`, `tbl_accounts` |
| DB column             | snake_case                 | `full_name`, `password_hash` |
| Package               | all lowercase              | `com.tomato.modules.user.service` |
| Boolean method        | `is`/`has`/`exists` prefix | `existsByUsername`           |

Every physical table name is prefixed with `tbl_` (e.g. entity `User` → `@Table(name = "tbl_users")`). The prefix applies only to table names, not columns, indexes, or constraints.

Repository query methods follow Spring Data naming: `findByX`, `existsByX`, `findByXAndY`.

---

## Lombok

Use Lombok to cut boilerplate. Approved annotations and where they belong:

| Annotation                 | Use on                                                                                                                                                       |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `@Getter`                  | Entity, DTO                                                                                                                                                  |
| `@Setter`                  | Entity only — not response DTOs (those are immutable)                                                                                                        |
| `@Builder`                 | Entity, response DTO                                                                                                                                         |
| `@NoArgsConstructor`       | Entity (JPA requires it), request DTO (Jackson requires it)                                                                                                  |
| `@AllArgsConstructor`      | Entity (needed alongside `@Builder`)                                                                                                                         |
| `@RequiredArgsConstructor` | Service, controller — constructor injection of `final` deps                                                                                                  |
| `@Data`                    | **Avoid on entities** — its generated `equals`/`hashCode` over all fields breaks JPA proxies and lazy loading. OK on plain value holders like `ApiResponse`. |

Entity pattern:

```java
@Entity
@Table(name = "tbl_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User { ... }
```

Request DTO (records are preferred for new DTOs):

```java
public record CreateUserRequest(
    @NotBlank String username,
    @Email @NotBlank String email
) {}
```

Response DTO (record with an entity factory):

```java
public record UserResponse(Integer id, String username, String email) {
    public static UserResponse from(User user) { ... }
}
```

---

## Dependency injection

Always constructor injection via `@RequiredArgsConstructor` with `private final` fields. Never `@Autowired` field injection — it hides dependencies and breaks testability/immutability.

```java
// Good
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
}

// Bad
@Service
public class UserServiceImpl {
    @Autowired
    private UserRepository userRepository;
}
```

---

## Service interface + impl

Every service is an interface plus exactly one implementation. Controllers depend on the interface.

```
UserService          ← interface in service/
UserServiceImpl      ← @Service class in service/, implements UserService
```

---

## DTO mapping

Response DTOs expose a static `from(Entity)` factory. Do not hand-build DTOs inline in controllers or services.

```java
// Good — in controller
return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));

// Bad — inline mapping scattered across callers
UserResponse.builder().id(user.getId()).email(user.getEmail())...
```

---

## Imports

- No wildcard imports (`import x.*`). Import each type explicitly.
- Group: java/jakarta std, third-party, then `com.tomato.*`.

---

## Nulls & Optionals

- Repository finders return `Optional<T>`. Resolve with `.orElse(null)` then guard via `ObjectsValidator`, or `.orElseThrow(...)`. Pick one style per method — see [exception.md](exception.md).
- Never return `null` collections from services — return empty list.

---

## Formatting

- 4-space indent, no tabs.
- One top-level class per file.
- Keep methods short; extract private helpers when a service method exceeds ~30 lines.
