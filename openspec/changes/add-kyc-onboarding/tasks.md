## 1. Enums, Entities, and Migration

- [x] 1.1 Add `CustomerType`, `OnboardingStatus`, `RiskLevel`, `VerificationStatus`, and `DocumentStatus` enums.
- [x] 1.2 Add JPA entities `CustomerProfile`, `KycVerification`, `VerificationDocument`, and `OnboardingAuditLog`.
- [x] 1.3 Declare schema on the entities via JPA annotations so Hibernate `ddl-auto` generates `customer_profiles`, `kyc_verifications`, `verification_documents`, and `onboarding_audit_logs`: `@Table` unique constraints (unique `user_id`, unique `profile_id`) and indexes (`status,updated_at`, `profile_id,document_type`, `profile_id,created_at`), NOT NULL columns, and the CCCD `^[0-9]{12}$` check via Hibernate `@Check` plus `@Pattern` on the KYC DTO. No Flyway/`V*.sql` files in this slice.

## 2. Repositories

- [x] 2.1 Add `CustomerProfileRepository` with `findByUserId`, `existsByUserId`, `findByStatus(Pageable)`.
- [x] 2.2 Add `KycVerificationRepository` with `findByProfileId`.
- [x] 2.3 Add `VerificationDocumentRepository` with `findByProfileId` and `existsByProfileIdAndDocumentTypeAndStatus`.
- [x] 2.4 Add `OnboardingAuditLogRepository` with `findByProfileIdOrderByCreatedAtAsc`.

## 3. Error Model

- [x] 3.1 Add error codes `2300`–`2308` to `ErrorCode` per the `ERROR_{HTTP}_{CODE}` convention.
- [x] 3.2 Extend `GlobalExceptionHandler` to map onboarding `403`, `404`, and `409` codes.

## 4. DTOs

- [x] 4.1 Add request DTOs `CreateOnboardingProfileRequest`, `UpsertKycRequest`, `AddVerificationDocumentRequest`, and `ReviewOnboardingRequest` with Bean Validation.
- [x] 4.2 Add `OnboardingStatusResponse` (high-level fields only) with a static factory from `CustomerProfile`.

## 5. OnboardingService (customer workflow)

- [x] 5.1 Add `OnboardingService` interface and implementation: `createProfile` (rejects duplicates), `getProfile`, `upsertKyc`, `addDocument`, `submit`, `requireApproved`.
- [x] 5.2 Enforce `customerType == INDIVIDUAL` for KYC operations and editable-state (`DRAFT`/`REQUIRES_MORE_INFO`) for all mutations.
- [x] 5.3 Add a central status-transition guard that throws `ERROR_409_2306` on illegal transitions.
- [x] 5.4 Implement `submit` validation: required KYC fields (`ERROR_400_2303`) and at least one identity document (`ERROR_400_2305`); move to `SUBMITTED` and set `submittedAt`.
- [x] 5.5 Implement `requireApproved` to throw `ERROR_403_2302` unless status is `APPROVED`.
- [x] 5.6 Write an `onboarding_audit_logs` row in the same transaction as every status transition.

## 6. Customer Controller

- [x] 6.1 Add onboarding controller for `POST /profile`, `GET /status`, `PUT /kyc`, `POST /documents`, `POST /submit`, all resolving the profile from the authenticated principal and wrapping responses in `ApiResponse<T>`.

## 7. Reviewer Workflow

- [x] 7.1 Add `OnboardingReviewService` interface and implementation: `listReviews(status, Pageable)`, `startReview` (`SUBMITTED→IN_REVIEW`), `decide` (`IN_REVIEW→APPROVED/REJECTED/REQUIRES_MORE_INFO`), requiring `reason` on reject/more-info and writing an audit row per decision.
- [x] 7.2 Add reviewer controller under `/api/admin/onboarding/**` behind a temporary admin gate (queue list, review detail, start, decision).

## 8. Banking Gate Integration

- [ ] 8.1 Call `onboardingService.requireApproved(userId)` before active account creation (when the account service exists).
- [ ] 8.2 Call `onboardingService.requireApproved(ownerUserId)` before deposit and withdraw (when money-movement services exist).

## 9. Tests

- [ ] 9.1 Unit tests: create profile once per user; duplicate returns `ERROR_409_2301`; KYC rejected on non-individual; submit missing KYC returns `ERROR_400_2303`; invalid transition returns `ERROR_409_2306`; approval writes an audit row; `requireApproved` rejects non-approved.
- [ ] 9.2 Integration tests: create profile → add KYC → add document metadata → submit; reviewer `SUBMITTED→IN_REVIEW→APPROVED`; account creation and deposit/withdraw rejected for non-approved onboarding.
- [ ] 9.3 Run `mvn test` and fix regressions.

## 10. Docs

- [ ] 10.1 Update `docs/design/api.md` and `docs/design/database.md` once contracts are stable.
