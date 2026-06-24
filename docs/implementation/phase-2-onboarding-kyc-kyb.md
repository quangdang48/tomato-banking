# Phase 2 - Onboarding (KYC / KYB)

Implements customer onboarding before active banking access. KYC verifies individual
customers; KYB verifies business customers and their beneficial owners. The result is one
onboarding decision that gates account activation and money movement.

> Delivery note: build this in two onboarding slices, not one large sprint. Phase 1 ships
> base auth + individual eKYC (`V1__auth_and_kyc.sql`). Phase 2 adds business KYB and
> beneficial owners (`V2__kyb_extension.sql`). Banking tables come later in Phase 3
> (`V3__banking_core.sql`). See [phased-delivery-plan.md](phased-delivery-plan.md).

> Scope: customer profile, KYC/KYB data capture, beneficial owners, document metadata,
> submit/review status workflow, audit log, API contracts, error codes, and service
> boundaries. External vendor integration, file storage provider selection, OCR, sanctions
> screening, and role-based reviewer security are **out of scope** for this phase.

Conventions this follows: [layers.md](../code-convention/layers.md),
[exception.md](../code-convention/exception.md),
[best-practices.md](../code-convention/best-practices.md). Related design docs:
[domain.md](../design/domain.md), [database.md](../design/database.md),
[api.md](../design/api.md).

---

## 0. Design goal

Onboarding is not a money mutation, but it is a prerequisite for safe banking access.
Keep it separate from account balance logic:

- Auth proves who the caller is.
- Onboarding proves whether the caller is allowed to bank.
- Account services check the onboarding decision before creating active accounts or
  allowing deposit/withdraw operations.

No controller should decide whether a customer is approved. That rule lives in services.

---

## 1. Domain model

### Customer type

```java
public enum CustomerType {
    INDIVIDUAL,
    BUSINESS
}
```

### Onboarding status

```java
public enum OnboardingStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    REQUIRES_MORE_INFO
}
```

Allowed transitions:

| From | To | Actor | Meaning |
|------|----|-------|---------|
| `DRAFT` | `SUBMITTED` | customer | Customer submits required data |
| `SUBMITTED` | `IN_REVIEW` | system/reviewer | Review starts |
| `IN_REVIEW` | `APPROVED` | reviewer/system | Customer may bank |
| `IN_REVIEW` | `REJECTED` | reviewer/system | Customer cannot bank |
| `IN_REVIEW` | `REQUIRES_MORE_INFO` | reviewer/system | Customer must update data |
| `REQUIRES_MORE_INFO` | `SUBMITTED` | customer | Customer resubmits |
| `REJECTED` | `SUBMITTED` | customer/admin | Optional appeal/resubmission path |

Do not allow direct jumps from `DRAFT` to `APPROVED`, or from `APPROVED` back to mutable
states without an explicit admin-only future phase.

### Risk level

```java
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
```

Risk is stored as an internal decision field. Do not expose full risk rationale in public
customer responses.

### Verification status

```java
public enum VerificationStatus {
    PENDING,
    VERIFIED,
    FAILED
}
```

### Document status

```java
public enum DocumentStatus {
    UPLOADED,
    ACCEPTED,
    REJECTED
}
```

---

## 2. Database tables

Keep identity data normalized and auditable. Store document files outside the DB; the DB
stores only metadata and a storage reference.

### customer_profiles

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| user_id | BIGINT | FK -> users.id, NOT NULL, UNIQUE |
| customer_type | VARCHAR | NOT NULL (`INDIVIDUAL` / `BUSINESS`) |
| status | VARCHAR | NOT NULL |
| risk_level | VARCHAR | nullable |
| submitted_at | TIMESTAMP | nullable |
| reviewed_at | TIMESTAMP | nullable |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

`user_id` is unique so one authenticated user has one onboarding profile in this phase.

### kyc_verifications

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK -> customer_profiles.id, NOT NULL, UNIQUE |
| legal_name | VARCHAR | NOT NULL |
| date_of_birth | DATE | NOT NULL |
| document_type | VARCHAR | NOT NULL (`CCCD` / `PASSPORT`) |
| document_number | VARCHAR | NOT NULL |
| street_address | VARCHAR | NOT NULL |
| district | VARCHAR | NOT NULL |
| province_city | VARCHAR | NOT NULL |
| status | VARCHAR | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

For CCCD, add a DB check: `document_number ~ '^[0-9]{12}$'`.

### kyb_verifications

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK -> customer_profiles.id, NOT NULL, UNIQUE |
| legal_business_name | VARCHAR | NOT NULL |
| registration_number | VARCHAR | NOT NULL |
| tax_id | VARCHAR | nullable |
| incorporation_date | DATE | nullable |
| business_address_line1 | VARCHAR | NOT NULL |
| district | VARCHAR | NOT NULL |
| province_city | VARCHAR | NOT NULL |
| industry | VARCHAR | nullable |
| status | VARCHAR | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

For Vietnam tax IDs, add a DB check: `tax_id ~ '^[0-9]{10}$' OR tax_id ~ '^[0-9]{10}-[0-9]{3}$'`.

### beneficial_owners

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| kyb_verification_id | BIGINT | FK -> kyb_verifications.id, NOT NULL |
| legal_name | VARCHAR | NOT NULL |
| date_of_birth | DATE | NOT NULL |
| ownership_percentage | NUMERIC(5,2) | NOT NULL, CHECK between 0 and 100 |
| document_type | VARCHAR | NOT NULL (`CCCD` / `PASSPORT`) |
| document_number | VARCHAR | NOT NULL |
| status | VARCHAR | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

KYB submission requires at least one beneficial owner. A stricter ownership threshold
(for example owners with 25% or more) can be added later when compliance rules are
finalized.

### verification_documents

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK -> customer_profiles.id, NOT NULL |
| owner_id | BIGINT | FK -> beneficial_owners.id, nullable |
| document_type | VARCHAR | NOT NULL |
| storage_key | VARCHAR | NOT NULL |
| original_filename | VARCHAR | NOT NULL |
| content_type | VARCHAR | NOT NULL |
| size_bytes | BIGINT | NOT NULL |
| status | VARCHAR | NOT NULL |
| rejection_reason | VARCHAR | nullable |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

`owner_id` is nullable because documents may belong to the profile itself or to a
beneficial owner.

### onboarding_audit_logs

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| profile_id | BIGINT | FK -> customer_profiles.id, NOT NULL |
| actor_user_id | BIGINT | FK -> users.id, nullable |
| action | VARCHAR | NOT NULL |
| old_status | VARCHAR | nullable |
| new_status | VARCHAR | nullable |
| reason | VARCHAR | nullable |
| created_at | TIMESTAMP | NOT NULL |

Every status transition writes an audit row in the same transaction.

### Indexes

| Table | Index | Why |
|-------|-------|-----|
| customer_profiles | unique(user_id) | one profile per user |
| customer_profiles | idx(status, updated_at) | reviewer queue |
| kyc_verifications | unique(profile_id) | one KYC record per profile |
| kyb_verifications | unique(profile_id) | one KYB record per profile |
| beneficial_owners | idx(kyb_verification_id) | list owners for KYB |
| verification_documents | idx(profile_id, document_type) | required document checks |
| onboarding_audit_logs | idx(profile_id, created_at) | audit timeline |

---

## 3. Error codes

Add onboarding codes in the `2300` block. Keep the `ERROR_{HTTP}_{CODE}` convention.

| code | name | HTTP | Meaning |
|------|------|------|---------|
| 2300 | `ERROR_404_2300` | 404 | Onboarding profile not found |
| 2301 | `ERROR_409_2301` | 409 | Onboarding profile already exists |
| 2302 | `ERROR_403_2302` | 403 | Onboarding is not approved |
| 2303 | `ERROR_400_2303` | 400 | Required KYC data is missing |
| 2304 | `ERROR_400_2304` | 400 | Required KYB data is missing |
| 2305 | `ERROR_400_2305` | 400 | Required document is missing |
| 2306 | `ERROR_409_2306` | 409 | Invalid onboarding status transition |
| 2307 | `ERROR_400_2307` | 400 | Beneficial owner is required |
| 2308 | `ERROR_409_2308` | 409 | Onboarding has been rejected |

```java
ERROR_404_2300(2300, "Onboarding profile not found"),
ERROR_409_2301(2301, "Onboarding profile already exists"),
ERROR_403_2302(2302, "Onboarding is not approved"),
ERROR_400_2303(2303, "Required KYC data is missing"),
ERROR_400_2304(2304, "Required KYB data is missing"),
ERROR_400_2305(2305, "Required document is missing"),
ERROR_409_2306(2306, "Invalid onboarding status transition"),
ERROR_400_2307(2307, "Beneficial owner is required"),
ERROR_409_2308(2308, "Onboarding has been rejected"),
```

Extend `GlobalExceptionHandler` status mapping for `403`, `404`, and `409`. Once auth,
banking, and onboarding codes all exist, move status mapping onto the enum as recommended
by [exception.md](../code-convention/exception.md).

---

## 4. DTOs

Requests use `@Getter @NoArgsConstructor` and Bean Validation. Responses use
`@Getter @Builder` with static factories.

### Create onboarding profile

```java
@Getter
@NoArgsConstructor
public class CreateOnboardingProfileRequest {
    @NotNull
    private CustomerType customerType;
}
```

### KYC request

```java
@Getter
@NoArgsConstructor
public class UpsertKycRequest {
    @NotBlank private String legalName;
    @NotNull private LocalDate dateOfBirth;
    @NotBlank private String documentType;
    @NotBlank private String documentNumber;
    @NotBlank private String streetAddress;
    @NotBlank private String district;
    @NotBlank private String provinceCity;
}
```

### KYB request

```java
@Getter
@NoArgsConstructor
public class UpsertKybRequest {
    @NotBlank private String legalBusinessName;
    @NotBlank private String registrationNumber;
    private String taxId;
    private LocalDate incorporationDate;
    @NotBlank private String businessAddressLine1;
    @NotBlank private String district;
    @NotBlank private String provinceCity;
    private String industry;
}
```

### Beneficial owner request

```java
@Getter
@NoArgsConstructor
public class BeneficialOwnerRequest {
    @NotBlank private String legalName;
    @NotNull private LocalDate dateOfBirth;
    @NotNull @DecimalMin("0.01") @DecimalMax("100.00")
    private BigDecimal ownershipPercentage;
    @NotBlank private String documentType;
    @NotBlank private String documentNumber;
}
```

### Document request

This phase can accept metadata only. Actual multipart upload can be added when the storage
strategy is selected.

```java
@Getter
@NoArgsConstructor
public class AddVerificationDocumentRequest {
    @NotBlank private String documentType;
    @NotBlank private String storageKey;
    @NotBlank private String originalFilename;
    @NotBlank private String contentType;
    @Positive private long sizeBytes;
    private Long ownerId;
}
```

### Review request

```java
@Getter
@NoArgsConstructor
public class ReviewOnboardingRequest {
    @NotNull private OnboardingStatus decision; // APPROVED, REJECTED, REQUIRES_MORE_INFO
    private RiskLevel riskLevel;
    private String reason;
}
```

### Response

Public customer responses should show status and high-level progress only. Do not return
document numbers, tax IDs, or full identity payloads from the status endpoint.

```java
@Getter
@Builder
public class OnboardingStatusResponse {
    private final Long profileId;
    private final CustomerType customerType;
    private final OnboardingStatus status;
    private final Instant submittedAt;
    private final Instant reviewedAt;

    public static OnboardingStatusResponse from(CustomerProfile profile) {
        return OnboardingStatusResponse.builder()
            .profileId(profile.getId())
            .customerType(profile.getCustomerType())
            .status(profile.getStatus())
            .submittedAt(profile.getSubmittedAt())
            .reviewedAt(profile.getReviewedAt())
            .build();
    }
}
```

Create separate reviewer response DTOs later if admin screens need full details.

---

## 5. API contract

All endpoints require JWT auth unless explicitly marked admin/reviewer. Every response uses
`ApiResponse<T>`.

### Customer endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/onboarding/profile` | yes | Create the caller's onboarding profile |
| GET | `/api/onboarding/status` | yes | Get caller's onboarding status |
| PUT | `/api/onboarding/kyc` | yes | Create/update individual KYC details |
| PUT | `/api/onboarding/kyb` | yes | Create/update business KYB details |
| POST | `/api/onboarding/beneficial-owners` | yes | Add beneficial owner for KYB |
| PUT | `/api/onboarding/beneficial-owners/{id}` | yes | Update beneficial owner |
| DELETE | `/api/onboarding/beneficial-owners/{id}` | yes | Remove beneficial owner before submit |
| POST | `/api/onboarding/documents` | yes | Add verification document metadata |
| POST | `/api/onboarding/submit` | yes | Submit onboarding for review |

Example:

```json
// POST /api/onboarding/profile
{ "customerType": "BUSINESS" }

// response.data
{ "profileId": 10, "customerType": "BUSINESS", "status": "DRAFT" }
```

```json
// POST /api/onboarding/submit
{}

// response.data
{ "profileId": 10, "customerType": "BUSINESS", "status": "SUBMITTED" }
```

### Reviewer endpoints

These need role-based access before they are exposed beyond dev/admin testing.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/admin/onboarding/reviews?status=SUBMITTED` | reviewer | List review queue |
| GET | `/api/admin/onboarding/reviews/{profileId}` | reviewer | Get full review details |
| POST | `/api/admin/onboarding/reviews/{profileId}/start` | reviewer | Move to `IN_REVIEW` |
| POST | `/api/admin/onboarding/reviews/{profileId}/decision` | reviewer | Approve/reject/request more info |

Reviewer decision request:

```json
{
  "decision": "APPROVED",
  "riskLevel": "LOW",
  "reason": "Verified manually"
}
```

---

## 6. Service boundaries

### `OnboardingService`

Owns customer-facing onboarding workflow.

```java
public interface OnboardingService {
    CustomerProfile createProfile(Long userId, CreateOnboardingProfileRequest request);
    CustomerProfile getProfile(Long userId);
    KycVerification upsertKyc(Long userId, UpsertKycRequest request);
    KybVerification upsertKyb(Long userId, UpsertKybRequest request);
    BeneficialOwner addBeneficialOwner(Long userId, BeneficialOwnerRequest request);
    BeneficialOwner updateBeneficialOwner(Long userId, Long ownerId, BeneficialOwnerRequest request);
    void deleteBeneficialOwner(Long userId, Long ownerId);
    VerificationDocument addDocument(Long userId, AddVerificationDocumentRequest request);
    CustomerProfile submit(Long userId);
    void requireApproved(Long userId);
}
```

Rules:

- `createProfile` rejects duplicate profiles.
- KYC operations require `customerType == INDIVIDUAL`.
- KYB and beneficial-owner operations require `customerType == BUSINESS`.
- Mutations are allowed only in `DRAFT` or `REQUIRES_MORE_INFO`.
- `submit` validates required fields and documents, then moves to `SUBMITTED`.
- `requireApproved` throws `ERROR_403_2302` unless status is `APPROVED`.

### `OnboardingReviewService`

Owns reviewer/admin workflow.

```java
public interface OnboardingReviewService {
    Page<CustomerProfile> listReviews(OnboardingStatus status, Pageable pageable);
    CustomerProfile startReview(Long reviewerUserId, Long profileId);
    CustomerProfile decide(Long reviewerUserId, Long profileId, ReviewOnboardingRequest request);
}
```

Rules:

- `startReview` allows only `SUBMITTED -> IN_REVIEW`.
- `decide` allows only `IN_REVIEW -> APPROVED/REJECTED/REQUIRES_MORE_INFO`.
- Every decision writes an audit log row in the same transaction.

### Account service integration

Before creating an active account:

```java
onboardingService.requireApproved(authenticatedUserId);
```

Before deposit or withdraw:

```java
onboardingService.requireApproved(account.getUser().getId());
```

This keeps onboarding policy centralized and avoids copying status checks into every
controller.

---

## 7. Repository methods

```java
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    Page<CustomerProfile> findByStatus(OnboardingStatus status, Pageable pageable);
}
```

```java
public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {
    Optional<KycVerification> findByProfileId(Long profileId);
}
```

```java
public interface KybVerificationRepository extends JpaRepository<KybVerification, Long> {
    Optional<KybVerification> findByProfileId(Long profileId);
}
```

```java
public interface BeneficialOwnerRepository extends JpaRepository<BeneficialOwner, Long> {
    List<BeneficialOwner> findByKybVerificationId(Long kybVerificationId);
    boolean existsByKybVerificationId(Long kybVerificationId);
}
```

```java
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, Long> {
    List<VerificationDocument> findByProfileId(Long profileId);
    boolean existsByProfileIdAndDocumentTypeAndStatus(
        Long profileId,
        String documentType,
        DocumentStatus status
    );
}
```

```java
public interface OnboardingAuditLogRepository extends JpaRepository<OnboardingAuditLog, Long> {
    List<OnboardingAuditLog> findByProfileIdOrderByCreatedAtAsc(Long profileId);
}
```

---

## 8. Validation rules

### Profile

- Authenticated user may have only one profile.
- `customerType` cannot change after creation in this phase.

### Individual KYC

Required before submit:

- legal name
- date of birth
- document type
- document number
- street address
- district
- province/city
- at least one identity document metadata row

### Business KYB

Required before submit:

- legal business name
- registration number
- business address line 1
- district
- province/city
- at least one beneficial owner
- at least one business document metadata row

### Status mutation

- Customers can edit only before final review (`DRAFT`, `REQUIRES_MORE_INFO`).
- Customers cannot submit already submitted/in-review/approved profiles.
- Reviewer decisions must include `reason` when rejecting or requesting more info.

---

## 9. Security and privacy

- Do not log document numbers, tax IDs, storage keys, or full identity payloads.
- Public customer status endpoints return only high-level onboarding state.
- Reviewer detail endpoints are admin/reviewer only.
- Document upload must validate content type and size before storage when implemented.
- Treat `storageKey` as sensitive. It should not be a public URL.
- Future vendor webhooks must verify signature before changing status.

---

## 10. Tests

Unit tests:

- create profile succeeds once per user
- duplicate profile returns `ERROR_409_2301`
- individual profile rejects KYB updates
- business profile rejects KYC updates
- submit individual profile with missing KYC returns `ERROR_400_2303`
- submit business profile without beneficial owner returns `ERROR_400_2307`
- invalid status transition returns `ERROR_409_2306`
- approval writes an audit log
- `requireApproved` rejects non-approved statuses

Integration tests:

- customer can create profile, add KYC, add document metadata, submit
- business can create profile, add KYB, add owner, add document metadata, submit
- reviewer can move `SUBMITTED -> IN_REVIEW -> APPROVED`
- account creation rejects user with non-approved onboarding
- deposit/withdraw rejects account whose owner's onboarding is not approved

---

## 11. Implementation order

1. Add enums, entities, repositories, and migrations.
2. Add onboarding error codes and handler status mapping.
3. Add DTOs and customer-facing `OnboardingService`.
4. Add customer onboarding controller.
5. Add submit validation and audit logging.
6. Add reviewer service/controller behind temporary admin protection.
7. Add `requireApproved` gate to account creation and money movement.
8. Add unit/integration tests.
9. Update `docs/design/api.md` and `docs/design/database.md` once the contracts are stable.

Keep document upload implementation metadata-only until the storage strategy is decided.

---

## 12. Out of scope for this phase

- Real KYC/KYB vendor integration.
- Sanctions, PEP, adverse media, or watchlist screening.
- OCR and document authenticity checks.
- Multipart file upload/storage implementation.
- Multiple business representatives per user.
- Re-verification after expiry.
- Admin role model beyond a minimal reviewer gate.
