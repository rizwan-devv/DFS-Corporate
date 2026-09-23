# Functional Specification Document (FSD)

**Product:** DFS Corporate Portal  
**Audience:** Bank / EMI Client (IT, Compliance Ops, Delivery)  
**Document ID:** DFS-CP-FSD-001  
**Version:** 1.0  
**Status:** Draft for Client Review  
**Date:** 17 September 2026  
**Related:** BRD DFS-CP-BRD-001 · Product Document DFS-CP-PD-001  

---

## Document control

| Field | Value |
|-------|-------|
| Product name | DFS Corporate Portal |
| Classification | Client confidential |
| Source baseline | Application codebase + PROJECT_AND_KYC.md |

### Revision history

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 17 Sep 2026 | Initial client-ready functional specification |

---

## 1. Purpose & scope

This FSD specifies **how** DFS Corporate Portal behaves: actors, roles, screens, APIs, data, workflows, integrations, and non-functional characteristics. It elaborates BRD requirements into implementable functional detail for Bank / EMI client review.

**In scope:** Web portal, platform admin, partner mobile KYC APIs, franchise flows, portal approvals, DFS/CMS integrations.  
**Out of scope detail:** Source-code listing, internal class design, and vendor commercial contracts (**TBD**).

---

## 2. System context

```
┌──────────────────────┐     JWT      ┌─────────────────────────────┐
│ DFS Corporate Portal │─────────────▶│ DFS Corporate API           │
│ (React web)          │              │ Spring Boot · port 8090     │
└──────────────────────┘              └──────────────┬──────────────┘
┌──────────────────────┐  session     │              │
│ Partner KYC Mobile   │──────────────┤              ├─ MySQL + Flyway
│ (Capacitor)          │  /app-kyc    │              ├─ File uploads
└──────────────────────┘              │              ├─ DFS Account API
┌──────────────────────┐              │              ├─ AgentApp Portal API
│ Platform Admin UI    │──────────────┘              └─ CMS Card APIs
└──────────────────────┘
```

| Layer | Technology |
|-------|------------|
| Web portal | Vite, React 19, TypeScript, React Router |
| Mobile KYC | Vite + React + Capacitor (`com.dfscorporate.kyc`) |
| API | Spring Boot 3.3, Java 21, Spring Security, JWT, JPA |
| Database | MySQL 8 (default), Flyway migrations |
| Files | Local disk upload directory (volume in Docker) |

---

## 3. Actors, roles & permissions

### 3.1 Account roles

| Role | Description | Typical access |
|------|-------------|----------------|
| `PLATFORM_ADMIN` | Platform back-office operator | `/admin`, `/api/admin/**` |
| `PARTY_USER` | User belonging to a corporate party | Portal APIs scoped to their party |

Account statuses: `PENDING_VERIFICATION` → `ACTIVE` / `LOCKED`.

### 3.2 Portal workflow roles

Assigned via `account_portal_roles`:

| Role | Capability |
|------|------------|
| `PARTY_ADMIN` | Manage portal users; elevated within party |
| `MAKER` | Create approval requests |
| `CHECKER` | First review |
| `APPROVER` | Second review (skippable if checker also has APPROVER) |
| `RELEASER` | Final release |

**Workflow sequence:** `MAKER → CHECKER → APPROVER → RELEASER → DONE`

**Approval types:** `GENERIC`, `PAYMENT`, `BULK_PAYMENT`, `COMMISSION_CHANGE`  
> Payment execution rails: **TBD / future** — types exist; end-to-end payment settlement not specified as live.

### 3.3 Party types

| Type | How created |
|------|-------------|
| `MERCHANT` | Self-signup |
| `SUB_MERCHANT` | Franchise invite from parent Merchant |

### 3.4 UI gating (functional)

| Area | Rule |
|------|------|
| Finance (balance, statement, cards, …) | Party `ACTIVE` (Merchant) |
| Network / ops (invites, franchises, approvals, portal users) | `MERCHANT` + `ACTIVE` |
| Admin | `PLATFORM_ADMIN` |

---

## 4. Functional modules

### 4.1 Public & authentication

| Function | Behaviour |
|----------|-----------|
| Landing | Explains Merchant / Sub-merchant paths and entity types |
| Getting started | Pre-onboarding checklist |
| Signup | Creates Merchant account; OTP required |
| Franchise onboard | Signup bound to invite token as Sub-merchant |
| Verify OTP | Activates email verification |
| Login | Issues JWT (default ~24h) |
| Reference LOVs | Party types, entity types, ID doc types, required documents |

**APIs**

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/auth/signup` | Merchant signup |
| POST | `/api/auth/verify-otp` | Email OTP |
| POST | `/api/auth/login` | JWT |
| GET | `/api/party-types`, `/api/entity-types`, `/api/id-document-types` | Reference |
| GET | `/api/party-types/{type}/documents` | Required docs matrix |
| GET | `/api/public/franchise-invite/{token}` | Validate invite |

### 4.2 Entity onboarding wizard

**Route:** `/onboarding`  
**Steps:** Entity → Partners (conditional) → Documents → Review / Submit  

| Capability | Detail |
|------------|--------|
| Entity CDD | Legal name, entity type, incorporation, NTN/tax, FATCA/CRS, addresses, nature/purpose, T&Cs |
| Geo evidence | IP + browser geolocation when permitted |
| Partners | Partnership/LLP roster (name, phone, email) |
| Documents | Multipart uploads per Annex-C matrix |
| Submit | Tracking ID; decision due (5 working days); status progression |
| Status | `GET /api/onboarding/status` |

**Entity types:** `SOLE_PROPRIETORSHIP`, `SMALL_BUSINESS`, `PARTNERSHIP`, `LLP`

**Document examples**

| Entity | Typical mandatory evidence |
|--------|----------------------------|
| Sole / Small | Owner ID front/back/photo + alternative business evidence |
| Partnership | Deed, authority letter, registration cert (unless unregistered) |
| LLP | Deed, SECP certificate, authority |
| Sub-merchant | Parent authority + authorised ID pack |

**APIs:** `GET/PUT /api/onboarding/me` (profile), associated persons CRUD, document upload, `POST /api/onboarding/submit`, partner-invite helpers.

**Party statuses:** `DRAFT` → `SUBMITTED` / `PENDING_APPROVAL` → `ACTIVE` | `REJECTED` (`SUSPENDED` supported). Draft resume window: **30 days**.

### 4.3 Partner mobile KYC (App)

**Purpose:** Natural-person KYC for partners / owners after business docs on portal.  
**Deep link pattern:** `dfscorporate://kyc?token=…`

| Step | API (prefix `/api/public/app-kyc`) | Result |
|------|-------------------------------------|--------|
| 1 | Login (phone+PIN or email+password) / invite | Session |
| 2 | OTP send / verify | Mobile verified; LOVs; video script |
| 3 | Change password | Temp PIN → password |
| 4 | Video challenge / verification (MP4) | Video KYC evidence |
| 5 | Submit multipart profile + media | `KYC_COMPLETED` |
| 6 | Optional fail / refresh LOVs | Ops |

**Media codes:** `CNIC_FRONT`, `CNIC_BACK`, `SELFIE` (fingerprints verified via NADRA — not stored)  
**CNIC normalisation:** stored/sent **without dashes** (13 digits).

When **all** required partner app KYCs complete → party moves to `PENDING_APPROVAL`.

> Secondary path: public web partner KYC via `/partner-kyc/:token` and `/api/public/partner-kyc/**` remains available; **primary** personal KYC path is the mobile app.

### 4.4 Platform administration

**Route:** `/admin` · **API:** `/api/admin/**` · **Role:** `PLATFORM_ADMIN`

| Function | Description |
|----------|-------------|
| Brand list | Soft multi-brand codes |
| Queues | Pending / all parties |
| Party detail | Profile, persons, docs, IP/geo, risk, screening, identity |
| Sanctions | Manual CLEAR / HIT workflow |
| Identity | Manual / waived verification status |
| Documents | Approve / reject / download |
| Decision | Approve (ACTIVE + email credentials) / Reject (reason) |
| Provision | Retry DFS `corporateonboarding` |
| Discrepancy | Notes to applicant |
| Partner ops | Resend invites / mark app KYC complete (ops aid) |

### 4.5 Post-activation Merchant portal

Shown when party is `ACTIVE` (network features for `MERCHANT`):

| Route | Function | API / integration |
|-------|----------|-------------------|
| `/dashboard` | Status, KYC progress, provision, KPIs | Composite |
| `/balance` | Agent balance | `/api/me/agent-balance` → Portal API |
| `/statement` | Mini-statement | `/api/me/agent-mini-statement` |
| `/transactions` | Franchise / commission view | Franchise services |
| `/cards` | Card search, detail, inquiry, status | CMS APIs |
| `/invites` | Create / resend / cancel franchise invites | Franchise invite APIs |
| `/franchises` | Children, confirm commission, child balance/statement/MPIN | Franchise + portal APIs |
| `/approvals` | Create & decide approval requests | Approval workflow APIs |
| `/portal-users` | Invite users; assign portal roles | Portal user APIs |
| `/profile` | Party profile | Onboarding me |

### 4.6 Franchise / commission

| Function | Behaviour |
|----------|-----------|
| Invite | Parent sets proposed commission %; email/link to child |
| Onboard | Child signs up as `SUB_MERCHANT` linked to parent |
| Confirm | Parent locks commission plan for child |
| Visibility | Parent can view network and child agent views when APIs enabled |

Ledger tables for commission amounts were removed in later schema; **% plans** remain. Commercial settlement engine: **TBD**.

---

## 5. End-to-end workflows

### 5.1 Merchant onboarding → provision

```
MERCHANT signup → OTP → /onboarding (Entity → Partners → Docs)
  → Submit (Tracking ID, decision_due_at)
  → Partner app invites (phone + temp PIN)
  → Each partner: App KYC → KYC_COMPLETED
  → Party PENDING_APPROVAL
  → Admin sanctions/docs review → Approve
  → Party ACTIVE + credentials email
  → DFS corporateonboarding (if DFS_ACCOUNT_API_ENABLED)
  → accountProvisionStatus SUCCESS | FAILED | PENDING
```

### 5.2 Sub-merchant

```
Parent /invites → token link → /franchise-onboard
  → SUB_MERCHANT signup → OTP → onboarding (+ PARENT_AUTH docs)
  → Partner/owner app KYC as required → Admin → ACTIVE → provision
  → Parent confirms commission plan
```

### 5.3 Approval workflow

```
PARTY_ADMIN assigns MAKER/CHECKER/APPROVER/RELEASER
  → MAKER creates approval_request
  → CHECKER decides → APPROVER (may skip) → RELEASER → APPROVED/REJECTED
  → Actions stored on approval_actions
```

---

## 6. Data specification (logical)

| Entity | Purpose |
|--------|---------|
| `parties` | Corporate applicant / activated customer |
| `accounts` | Login users (multi-user per party supported) |
| `account_portal_roles` | Maker–checker roles |
| `otp_codes` | OTP challenges |
| `required_documents` / `party_documents` | Matrix + uploads |
| `associated_persons` | Partners / related persons |
| `partner_invites` | Legacy web partner KYC tokens |
| `partner_app_users` | Mobile KYC users & session |
| `video_challenges` | Read-aloud video challenges |
| `franchise_invites` | Child invites + proposed commission |
| `franchise_commission_plans` | Locked % plans |
| `approval_requests` / `approval_actions` | Workflow |
| `brands` | Soft multi-tenant brand |

**Key enums:** `PartyStatus`, `AccountProvisionStatus`, `PartnerAppKycStatus`, `ScreeningStatus`, `IdentityVerificationStatus`, `RiskRating`, `DocumentStatus`, `FranchiseInviteStatus`, `VideoVerificationStatus`.

---

## 7. Integrations

| Integration | Flag / key | Functions |
|-------------|------------|-----------|
| DFS Account API | `DFS_ACCOUNT_API_ENABLED` | `corporateonboarding`, LOVs, segments |
| AgentApp Portal API | `DFS_PORTAL_API_ENABLED` + portal key | Balance, mini-statement, change MPIN |
| CMS Card APIs | `DFS_CMS_API_ENABLED` | Search / get / status / inquiry / LOVs |
| Email | `MAIL_ENABLED` | OTP, invites, credentials (dev may log only) |

Environment URLs and credentials: **TBD per client environment**.

---

## 8. Non-functional specification

| Area | Specification |
|------|---------------|
| Auth | Stateless JWT bearer; BCrypt for stored passwords |
| App KYC session | Separate session tokens on partner app users |
| CORS | Allow-list configured per deployment |
| Uploads | Configurable max file / request size (large media supported) |
| Health | `/actuator/health` |
| Sensitive handling | Partner password plain retained until DFS provision (integration constraint); harden for production — **TBD** client security review |
| Multi-tenancy | `brand_id` soft tenancy — hard isolation **TBD** |
| Retention / encryption at rest | **TBD** |
| Availability SLA | **TBD** |
| Accessibility / i18n | Not formally certified — **TBD** |

---

## 9. Screen inventory (web)

| Route | Screen | Auth |
|-------|--------|------|
| `/` | Home | Public |
| `/getting-started` | Getting started | Public |
| `/signup` | Merchant signup | Public |
| `/franchise-onboard` | Franchise signup | Public (token) |
| `/verify-otp` | OTP verify | Public |
| `/login` | Login | Public |
| `/onboarding` | KYC wizard | Party user |
| `/partner-kyc/:token` | Web partner KYC | Token |
| `/admin` | Back-office | Platform admin |
| `/dashboard` | Dashboard | Authenticated |
| `/balance` | Balance | ACTIVE |
| `/statement` | Statement | ACTIVE |
| `/transactions` | Network transactions view | ACTIVE |
| `/cards` | Cards | ACTIVE |
| `/invites` | Franchise invites | MERCHANT ACTIVE |
| `/franchises` | Franchise network | MERCHANT ACTIVE |
| `/approvals` | Approvals | MERCHANT ACTIVE |
| `/portal-users` | Portal users | MERCHANT ACTIVE |
| `/profile` | Profile | Authenticated |

---

## 10. Error & status handling (functional)

| Situation | Expected behaviour |
|-----------|-------------------|
| Invalid OTP | Reject; allow resend per OTP policy |
| Incomplete Annex-C pack | Block submit with validation messages |
| Partners incomplete | Do not enter PENDING_APPROVAL until app KYCs complete |
| Admin reject | Party REJECTED; reason available to applicant |
| Provision failure | Party may still be ACTIVE; provision status FAILED; admin retry |
| Portal/CMS API disabled | UI/API returns controlled unavailable / empty — no silent success claim |

---

## 11. Traceability (BRD → FSD)

| BRD ID | FSD coverage |
|--------|--------------|
| BR-ONB-* | §4.1–4.3, §5.1–5.2 |
| BR-CMP-* | §4.4 |
| BR-PRT-* | §4.5–4.6, §5.3 |
| BR-NFR-* | §8 |

---

## 12. Open functional items (TBD)

1. Live NADRA BV/Verisys replace manual identity path  
2. Automated sanctions vendor replace manual CLEAR  
3. Payment / bulk-payment approval execution  
4. Formal retention, purge, encryption standards  
5. Hard multi-brand isolation if required by Bank  
6. Production notification channel (SMS vs email) policy  
7. UAT scripts & regulatory evidence pack  

---

*End of FSD*
