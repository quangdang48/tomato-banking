# UI Flow

Text UI wireframes for the customer journey from registration to approved banking access.
The screens follow the phased delivery plan:
[phased-delivery-plan.md](../implementation/phased-delivery-plan.md).

This is not a frontend design system. It is a product-flow map that helps connect screens
to API endpoints, services, and database tables.

---

## End-to-end flow

```text
+----------+     +-------+     +-------------------+     +-------------+
| Register | --> | Login | --> | Choose profile    | --> | KYC or KYB  |
| /auth    |     | JWT   |     | Profile type      |     | onboarding  |
+----------+     +-------+     +-------------------+     +-------------+
                                                             |
                                                             v
+---------------+     +----------------+     +-------------------------+
| Banking       | <-- | Admin approval | <-- | Submit onboarding       |
| accounts/txns |     | APPROVED       |     | SUBMITTED / IN_REVIEW   |
+---------------+     +----------------+     +-------------------------+
```

Rules:

- Registration creates the login identity in `users`.
- KYC/KYB creates and verifies the legal customer profile.
- Banking screens are available only after onboarding is `APPROVED`.

---

## Phase 1 - Auth and Individual eKYC

### Screen 1.1 - Register

Screen data:

```json
{
  "initialForm": {
    "username": "",
    "email": "",
    "password": "",
    "confirmPassword": ""
  },
  "demoInput": {
    "username": "quanghd_99",
    "email": "quang.ha@student.hcmute.edu.vn",
    "fullName": "HA DANG QUANG",
    "password": "secret123",
    "confirmPassword": "secret123"
  },
  "expectedResponse": {
    "id": 1001,
    "username": "quanghd_99",
    "email": "quang.ha@student.hcmute.edu.vn",
    "fullName": "HA DANG QUANG"
  }
}
```

Purpose: create the system identity used for login and ownership.

```text
+------------------------------------------------+
|                    REGISTER                    |
|                                                |
|  Username                                      |
|  [ quanghd_99                                ] |
|                                                |
|  Email                                         |
|  [ quang.ha@student.hcmute.edu.vn            ] |
|                                                |
|  Password                                      |
|  [ **********                               ]  |
|                                                |
|  Confirm password                              |
|  [ **********                               ]  |
|                                                |
|                 [ REGISTER NOW ]               |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Register | `POST /api/auth/register` | `AuthService` / `UserService` | `users` |

Notes:

- This is not KYC. It only creates the login identity.
- Password must be bcrypt-hashed before persistence.

### Screen 1.2 - Choose Profile Type

Screen data:

```json
{
  "display": {
    "userId": 1001,
    "availableCustomerTypes": [
      { "value": "INDIVIDUAL", "label": "Individual account" },
      { "value": "BUSINESS", "label": "Business account" }
    ],
    "selectedCustomerType": "INDIVIDUAL"
  },
  "submitPayload": {
    "customerType": "INDIVIDUAL"
  },
  "expectedResponse": {
    "profileId": 2001,
    "customerType": "INDIVIDUAL",
    "status": "DRAFT"
  }
}
```

Purpose: initialize the legal onboarding profile after login.

```text
+------------------------------------------------+
|              START VERIFICATION                |
|                                                |
|  Choose the profile type to activate banking:  |
|                                                |
|  +------------------------------------------+  |
|  | (o) Individual account                   |  |
|  |     For personal banking use             |  |
|  +------------------------------------------+  |
|                                                |
|  +------------------------------------------+  |
|  | ( ) Business account                     |  |
|  |     For companies and organizations      |  |
|  +------------------------------------------+  |
|                                                |
|                   [ CONTINUE ]                 |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Continue | `POST /api/onboarding/profile` | `OnboardingService.createProfile` | `customer_profiles` |

Phase behavior:

- Phase 1 supports `INDIVIDUAL`.
- Phase 2 enables `BUSINESS`.

### Screen 1.3 - Individual KYC

Screen data:

```json
{
  "display": {
    "profileId": 2001,
    "customerType": "INDIVIDUAL",
    "status": "DRAFT",
    "documentTypes": ["CCCD", "PASSPORT"],
    "provinceCityOptions": ["Ho Chi Minh City", "Ha Noi", "Da Nang"],
    "districtOptions": ["Thu Duc", "District 1", "District 3"]
  },
  "kycPayload": {
    "legalName": "HA DANG QUANG",
    "dateOfBirth": "1999-12-25",
    "documentType": "CCCD",
    "documentNumber": "079201001234",
    "streetAddress": "1 Vo Van Ngan",
    "district": "Thu Duc",
    "provinceCity": "Ho Chi Minh City"
  },
  "documentPayloads": [
    {
      "documentType": "CCCD_FRONT",
      "storageKey": "demo/kyc/2001/cccd-front.png",
      "originalFilename": "cccd-front.png",
      "contentType": "image/png",
      "sizeBytes": 245000
    },
    {
      "documentType": "CCCD_BACK",
      "storageKey": "demo/kyc/2001/cccd-back.png",
      "originalFilename": "cccd-back.png",
      "contentType": "image/png",
      "sizeBytes": 238000
    }
  ],
  "expectedSubmitResponse": {
    "profileId": 2001,
    "customerType": "INDIVIDUAL",
    "status": "SUBMITTED"
  }
}
```

Purpose: collect Vietnam-focused personal identity data and CCCD/passport document
metadata.

```text
+------------------------------------------------+
|               PERSONAL INFORMATION             |
|                                                |
|  Full legal name on CCCD/passport              |
|  [ HA DANG QUANG                             ] |
|                                                |
|  Document type                                 |
|  [ CCCD                                    v ] |
|                                                |
|  CCCD number (12 digits)                       |
|  [ 079201001234                             ] |
|                                                |
|  Date of birth                                 |
|  [ 25 / 12 / 1999                           ] |
|                                                |
|  Permanent address                             |
|  Province/City: [ Ho Chi Minh City        v ] |
|  District:      [ Thu Duc                 v ] |
|  Street:        [ 1 Vo Van Ngan             ] |
|                                                |
|  CCCD photos                                    |
|  +------------------+   +------------------+   |
|  | [ Front image ]  |   | [ Back image  ]  |   |
|  +------------------+   +------------------+   |
|                                                |
|              [ SUBMIT FOR REVIEW ]             |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Save KYC | `PUT /api/onboarding/kyc` | `OnboardingService.upsertKyc` | `kyc_verifications` |
| Add document | `POST /api/onboarding/documents` | `OnboardingService.addDocument` | `verification_documents` |
| Submit | `POST /api/onboarding/submit` | `OnboardingService.submit` | `customer_profiles`, `onboarding_audit_logs` |

Validation:

- CCCD must be 12 digits.
- Required address fields: `provinceCity`, `district`, `streetAddress`.
- At least one identity document metadata row is required before submit.

---

## Phase 2 - Business KYB Extension

This branch starts when the user chooses `BUSINESS` on the profile-type screen.

### Screen 2.1 - Business KYB

Screen data:

```json
{
  "display": {
    "profileId": 2002,
    "customerType": "BUSINESS",
    "status": "DRAFT",
    "provinceCityOptions": ["Ho Chi Minh City", "Ha Noi", "Da Nang"],
    "districtOptions": ["District 1", "District 3", "Thu Duc"]
  },
  "kybPayload": {
    "legalBusinessName": "CONG TY TNHH CONG NGHE NAYAMI",
    "registrationNumber": "0312345678",
    "taxId": "0312345678",
    "incorporationDate": "2020-01-15",
    "businessAddressLine1": "123 Le Loi",
    "district": "District 1",
    "provinceCity": "Ho Chi Minh City",
    "industry": "FINTECH"
  },
  "documentPayload": {
    "documentType": "BUSINESS_REGISTRATION",
    "storageKey": "demo/kyb/2002/gpkd-nayami.pdf",
    "originalFilename": "gpkd_nayami.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 1200000
  }
}
```

Purpose: collect company identity, Vietnam tax/registration data, address, and business
registration document metadata.

```text
+------------------------------------------------+
|               BUSINESS INFORMATION             |
|                                                |
|  Legal business name                           |
|  [ CONG TY TNHH CONG NGHE NAYAMI             ] |
|                                                |
|  Tax ID / business registration number         |
|  [ 0312345678                               ]  |
|                                                |
|  Head office address                           |
|  Province/City: [ Ho Chi Minh City        v ] |
|  District:      [ District 1              v ] |
|  Address:       [ 123 Le Loi                ] |
|                                                |
|  Business registration file                    |
|  +------------------------------------------+  |
|  | [file] gpkd_nayami.pdf          1.2 MB   |  |
|  +------------------------------------------+  |
|                                                |
|                    [ CONTINUE ]                |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Save KYB | `PUT /api/onboarding/kyb` | `OnboardingService.upsertKyb` | `kyb_verifications` |
| Add document | `POST /api/onboarding/documents` | `OnboardingService.addDocument` | `verification_documents` |

Validation:

- Vietnam tax ID format: 10 digits, or `10 digits-3 digits`.
- Required address fields: `provinceCity`, `district`, `businessAddressLine1`.

### Screen 2.2 - Beneficial Owners

Screen data:

```json
{
  "display": {
    "kybVerificationId": 5001,
    "owners": [
      {
        "ownerId": 6001,
        "legalName": "NGUYEN VAN A",
        "ownershipPercentage": 51.00,
        "documentMasked": "079201******",
        "hasDocument": true
      }
    ]
  },
  "addOwnerPayload": {
    "legalName": "TRAN THI B",
    "dateOfBirth": "1992-05-20",
    "ownershipPercentage": 30.00,
    "documentType": "CCCD",
    "documentNumber": "079202005678"
  },
  "ownerDocumentPayload": {
    "ownerId": 6002,
    "documentType": "OWNER_CCCD_FRONT",
    "storageKey": "demo/kyb/2002/owners/6002/cccd-front.png",
    "originalFilename": "owner-cccd-front.png",
    "contentType": "image/png",
    "sizeBytes": 230000
  },
  "expectedSubmitResponse": {
    "profileId": 2002,
    "customerType": "BUSINESS",
    "status": "SUBMITTED"
  }
}
```

Purpose: collect ultimate beneficial owner information for shareholders/owners with
significant ownership.

```text
+------------------------------------------------+
|          ULTIMATE BENEFICIAL OWNERS            |
|                                                |
|  Owners with more than 25% ownership:          |
|                                                |
|  +------------------------------------------+  |
|  | 1. Nguyen Van A (51%)                    |  |
|  |    CCCD: 079201****** - document added   |  |
|  +------------------------------------------+  |
|                                                |
|  [ + ADD BENEFICIAL OWNER ]                    |
|                                                |
|  +------------------------------------------+  |
|  | Owner name:      [ Tran Thi B           ] |  |
|  | Ownership:       [ 30   ] %              |  |
|  | Document type:   [ CCCD              v ] |  |
|  | Document number: [ 079202005678        ] |  |
|  | Owner CCCD file: [ Choose file         ] |  |
|  +------------------------------------------+  |
|                                                |
|          [ SUBMIT BUSINESS FOR REVIEW ]        |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Add owner | `POST /api/onboarding/beneficial-owners` | `OnboardingService.addBeneficialOwner` | `beneficial_owners` |
| Update owner | `PUT /api/onboarding/beneficial-owners/{id}` | `OnboardingService.updateBeneficialOwner` | `beneficial_owners` |
| Add owner document | `POST /api/onboarding/documents` | `OnboardingService.addDocument` | `verification_documents.owner_id` |
| Submit | `POST /api/onboarding/submit` | `OnboardingService.submit` | `customer_profiles`, `onboarding_audit_logs` |

Validation:

- At least one beneficial owner is required before business submit.
- `ownershipPercentage` must be greater than 0 and at most 100.
- Owner document type must be `CCCD` or `PASSPORT`.

---

## Admin / Reviewer

### Screen A.1 - Onboarding Review

Screen data:

```json
{
  "queueItem": {
    "profileId": 2001,
    "customerType": "INDIVIDUAL",
    "legalName": "HA DANG QUANG",
    "status": "SUBMITTED",
    "riskLevel": "LOW",
    "submittedAt": "2026-06-24T09:30:00Z"
  },
  "reviewDetail": {
    "profileId": 2001,
    "customerType": "INDIVIDUAL",
    "status": "IN_REVIEW",
    "riskLevel": "LOW",
    "kyc": {
      "legalName": "HA DANG QUANG",
      "dateOfBirth": "1999-12-25",
      "documentType": "CCCD",
      "documentNumber": "079201001234",
      "streetAddress": "1 Vo Van Ngan",
      "district": "Thu Duc",
      "provinceCity": "Ho Chi Minh City"
    },
    "documents": [
      { "documentType": "CCCD_FRONT", "status": "UPLOADED" },
      { "documentType": "CCCD_BACK", "status": "UPLOADED" }
    ]
  },
  "approvePayload": {
    "decision": "APPROVED",
    "riskLevel": "LOW",
    "reason": "Verified manually"
  }
}
```

Purpose: internal review screen for approving, rejecting, or requesting more information.

```text
+------------------------------------------------+
|              ONBOARDING REVIEW                 |
|                                                |
|  Profile: HA DANG QUANG       ID: 1002         |
|  Type: INDIVIDUAL             Risk: LOW        |
|  Status: IN_REVIEW                             |
|                                                |
|  Identity document       Extracted / entered   |
|  +----------------+      Name: HA DANG QUANG   |
|  |                |      No:   079201001234    |
|  |  CCCD image    |      DOB:  25/12/1999      |
|  |                |                             |
|  +----------------+                             |
|                                                |
|  Rejection / more-info reason                  |
|  [                                            ] |
|                                                |
|     [ REJECT ]  [ MORE INFO ]  [ APPROVE ]       |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Load queue | `GET /api/admin/onboarding/reviews` | `OnboardingReviewService.listReviews` | `customer_profiles` |
| Start review | `POST /api/admin/onboarding/reviews/{profileId}/start` | `OnboardingReviewService.startReview` | `customer_profiles`, `onboarding_audit_logs` |
| Decide | `POST /api/admin/onboarding/reviews/{profileId}/decision` | `OnboardingReviewService.decide` | `customer_profiles`, `onboarding_audit_logs` |

Status outcomes:

- `APPROVED`: account creation and banking operations may proceed.
- `REJECTED`: customer cannot bank.
- `REQUIRES_MORE_INFO`: customer can edit onboarding data and resubmit.

---

## Phase 3 - Banking Core

### Screen 3.1 - Account Dashboard

Screen data:

```json
{
  "display": {
    "user": {
      "id": 1001,
      "fullName": "HA DANG QUANG"
    },
    "onboarding": {
      "profileId": 2001,
      "status": "APPROVED",
      "displayStatus": "VERIFIED"
    },
    "account": {
      "accountId": 8001,
      "accountNumber": "10223456789",
      "status": "ACTIVE",
      "balance": 15250000.0000,
      "currency": "VND"
    },
    "recentTransactions": [
      {
        "transactionId": 9001,
        "type": "DEPOSIT",
        "amount": 5000000.0000,
        "balanceAfter": 17250000.0000,
        "referenceId": "DEP83742",
        "createdAt": "2026-06-24T09:45:00Z"
      },
      {
        "transactionId": 9002,
        "type": "WITHDRAW",
        "amount": 2000000.0000,
        "balanceAfter": 15250000.0000,
        "referenceId": "WTH11204",
        "createdAt": "2026-06-23T14:20:00Z"
      }
    ]
  },
  "depositPayload": {
    "amount": 5000000.0000,
    "referenceId": "DEP83742"
  },
  "withdrawPayload": {
    "amount": 2000000.0000,
    "referenceId": "WTH11204"
  }
}
```

Purpose: show the approved user's account, balance, and recent transaction history.

```text
+------------------------------------------------+
|  HELLO, HA DANG QUANG                          |
|  Onboarding status: [ VERIFIED ]               |
|                                                |
|  Payment account                               |
|  +------------------------------------------+  |
|  | Account no: 10223456789       ACTIVE     |  |
|  | Balance:    15,250,000.0000 VND          |  |
|  +------------------------------------------+  |
|                                                |
|        [ DEPOSIT ]             [ WITHDRAW ]    |
|                                                |
|  Recent transactions                           |
|  + Deposit   Ref: DEP83742      +5,000,000 VND |
|    2026-06-24 09:45                            |
|  - Withdraw  Ref: WTH11204      -2,000,000 VND |
|    2026-06-23 14:20                            |
+------------------------------------------------+
```

Backend mapping:

| UI action | API | Service | Tables |
|-----------|-----|---------|--------|
| Load dashboard | `GET /api/accounts/{id}/balance` + `GET /api/accounts/{id}/transactions` | `AccountService` / `TransactionService` | `accounts`, `transactions` |
| Deposit | `POST /api/accounts/{id}/deposit` | `AccountService.deposit` | `accounts`, `transactions` |
| Withdraw | `POST /api/accounts/{id}/withdraw` | `AccountService.withdraw` | `accounts`, `transactions` |

Rules:

- Dashboard is available only when onboarding is `APPROVED`.
- Balance mutation must be transactional.
- Every balance change must write a transaction row.
- Deposit/withdraw must include `referenceId` for idempotency.

---

## Screen-to-phase map

| Phase | Screens | Main tables |
|-------|---------|-------------|
| Phase 1 | Register, choose individual profile, KYC, documents | `users`, `customer_profiles`, `kyc_verifications`, `verification_documents`, `onboarding_audit_logs` |
| Phase 2 | Business KYB, beneficial owners | `kyb_verifications`, `beneficial_owners`, `verification_documents.owner_id` |
| Admin | Review queue and decisions | `customer_profiles`, `onboarding_audit_logs` |
| Phase 3 | Account dashboard, deposit, withdraw, transaction history | `accounts`, `transactions` |

---

