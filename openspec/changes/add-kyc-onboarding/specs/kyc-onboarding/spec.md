## ADDED Requirements

### Requirement: Create onboarding profile
The system SHALL allow an authenticated user to create exactly one onboarding profile, resolved from the authenticated principal, and SHALL reject a second profile for the same user.

#### Scenario: First profile created
- **WHEN** an authenticated user with no existing profile calls `POST /api/onboarding/profile` with `customerType` `INDIVIDUAL`
- **THEN** the system creates a profile in status `DRAFT` and returns HTTP 201 with `ApiResponse.data` containing `profileId`, `customerType`, and `status`

#### Scenario: Duplicate profile rejected
- **WHEN** an authenticated user who already has a profile calls `POST /api/onboarding/profile`
- **THEN** the system returns HTTP 409 with `ERROR_409_2301`

#### Scenario: Customer type cannot change after creation
- **WHEN** a user attempts to change `customerType` on an existing profile
- **THEN** the system does not change the stored `customerType`

### Requirement: View onboarding status
The system SHALL return the caller's high-level onboarding status without exposing identity payloads.

#### Scenario: Status returned for caller
- **WHEN** an authenticated user with a profile calls `GET /api/onboarding/status`
- **THEN** the system returns HTTP 200 with `profileId`, `customerType`, `status`, `submittedAt`, and `reviewedAt`

#### Scenario: No profile yet
- **WHEN** an authenticated user without a profile calls `GET /api/onboarding/status`
- **THEN** the system returns HTTP 404 with `ERROR_404_2300`

#### Scenario: Sensitive fields not exposed
- **WHEN** the status response is returned
- **THEN** it MUST NOT include document numbers, full identity payloads, storage keys, or risk rationale

### Requirement: Capture individual KYC data
The system SHALL allow a customer to create or update individual KYC details while the profile is editable, and only for an `INDIVIDUAL` profile.

#### Scenario: KYC upserted in editable state
- **WHEN** a user with an `INDIVIDUAL` profile in `DRAFT` or `REQUIRES_MORE_INFO` calls `PUT /api/onboarding/kyc` with legal name, date of birth, document type, document number, street address, district, and province/city
- **THEN** the system stores the KYC verification record and returns HTTP 200

#### Scenario: CCCD document number format enforced
- **WHEN** a KYC request uses document type `CCCD` with a document number that is not exactly 12 digits
- **THEN** the system rejects the request with HTTP 400

#### Scenario: KYC rejected for non-individual profile
- **WHEN** a user whose profile is not `INDIVIDUAL` calls `PUT /api/onboarding/kyc`
- **THEN** the system rejects the request and does not store KYC data

#### Scenario: KYC rejected when not editable
- **WHEN** a user calls `PUT /api/onboarding/kyc` while the profile is `SUBMITTED`, `IN_REVIEW`, or `APPROVED`
- **THEN** the system returns HTTP 409 with `ERROR_409_2306`

### Requirement: Capture verification document metadata
The system SHALL allow a customer to attach verification document metadata to their profile while editable, storing only metadata and a storage reference.

#### Scenario: Document metadata added
- **WHEN** a user in an editable state calls `POST /api/onboarding/documents` with document type, storage key, original filename, content type, and positive size
- **THEN** the system stores the document metadata in status `UPLOADED` and returns HTTP 200

#### Scenario: Storage key treated as sensitive
- **WHEN** document metadata is stored or returned
- **THEN** the `storageKey` MUST NOT be exposed as a public URL and MUST NOT be logged

### Requirement: Submit onboarding for review
The system SHALL validate required KYC data and documents on submit, move an editable profile to `SUBMITTED`, and reject submission of profiles not in an editable state.

#### Scenario: Successful submit
- **WHEN** a user with complete required KYC data and at least one identity document metadata row calls `POST /api/onboarding/submit` from `DRAFT` or `REQUIRES_MORE_INFO`
- **THEN** the system sets status to `SUBMITTED`, records `submittedAt`, writes an audit row, and returns HTTP 200

#### Scenario: Missing required KYC data
- **WHEN** an individual user submits with incomplete required KYC fields
- **THEN** the system returns HTTP 400 with `ERROR_400_2303`

#### Scenario: Missing required document
- **WHEN** an individual user submits with no identity document metadata row
- **THEN** the system returns HTTP 400 with `ERROR_400_2305`

#### Scenario: Resubmit already submitted profile rejected
- **WHEN** a user calls `POST /api/onboarding/submit` while status is `SUBMITTED`, `IN_REVIEW`, or `APPROVED`
- **THEN** the system returns HTTP 409 with `ERROR_409_2306`

### Requirement: Enforce onboarding status transitions
The system SHALL allow only defined onboarding status transitions and SHALL reject any other transition.

#### Scenario: Illegal transition rejected
- **WHEN** any actor attempts a transition not in the allowed set (for example `DRAFT` directly to `APPROVED`)
- **THEN** the system returns HTTP 409 with `ERROR_409_2306` and does not change the status

#### Scenario: Allowed transitions accepted
- **WHEN** a transition is one of `DRAFT→SUBMITTED`, `SUBMITTED→IN_REVIEW`, `IN_REVIEW→APPROVED`, `IN_REVIEW→REJECTED`, `IN_REVIEW→REQUIRES_MORE_INFO`, or `REQUIRES_MORE_INFO→SUBMITTED`
- **THEN** the system applies the new status

### Requirement: Reviewer onboarding workflow
The system SHALL expose reviewer endpoints, behind an admin gate, to list the review queue, start review, and record a decision, and SHALL write an audit row for every decision in the same transaction.

#### Scenario: List review queue
- **WHEN** a reviewer calls `GET /api/admin/onboarding/reviews?status=SUBMITTED`
- **THEN** the system returns a page of profiles in that status

#### Scenario: Start review
- **WHEN** a reviewer calls start review on a `SUBMITTED` profile
- **THEN** the system moves it to `IN_REVIEW` and writes an audit row

#### Scenario: Start review on non-submitted profile rejected
- **WHEN** a reviewer calls start review on a profile that is not `SUBMITTED`
- **THEN** the system returns HTTP 409 with `ERROR_409_2306`

#### Scenario: Approve decision
- **WHEN** a reviewer decides `APPROVED` on an `IN_REVIEW` profile
- **THEN** the system sets status `APPROVED`, records `reviewedAt`, and writes an audit row in the same transaction

#### Scenario: Reason required when rejecting or requesting more info
- **WHEN** a reviewer decides `REJECTED` or `REQUIRES_MORE_INFO` without a `reason`
- **THEN** the system rejects the request

#### Scenario: Decision on non-in-review profile rejected
- **WHEN** a reviewer submits a decision on a profile that is not `IN_REVIEW`
- **THEN** the system returns HTTP 409 with `ERROR_409_2306`

### Requirement: Onboarding gates banking access
The system SHALL provide a single policy operation that allows a banking action only when the user's onboarding is `APPROVED`.

#### Scenario: Approved user passes the gate
- **WHEN** `requireApproved` is called for a user whose profile status is `APPROVED`
- **THEN** the call returns without error

#### Scenario: Non-approved user blocked
- **WHEN** `requireApproved` is called for a user whose profile status is not `APPROVED`
- **THEN** the system throws `BusinessException` with `ERROR_403_2302`

#### Scenario: Account creation blocked for non-approved onboarding
- **WHEN** a user whose onboarding is not `APPROVED` attempts to create an active account
- **THEN** the system returns HTTP 403 with `ERROR_403_2302`

#### Scenario: Money movement blocked for non-approved onboarding
- **WHEN** a deposit or withdraw is attempted on an account whose owner's onboarding is not `APPROVED`
- **THEN** the system returns HTTP 403 with `ERROR_403_2302`
