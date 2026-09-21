# Product Document

**Product:** DFS Corporate Portal  
**Audience:** Bank / EMI Client (Business, Product, Compliance)  
**Document ID:** DFS-CP-PD-001  
**Version:** 1.0  
**Status:** Draft for Client Review  
**Date:** 17 September 2026  
**Related:** BRD DFS-CP-BRD-001 · FSD DFS-CP-FSD-001  

---

## Document control

| Field | Value |
|-------|-------|
| Product name | DFS Corporate Portal |
| Classification | Client confidential |
| Purpose | Product positioning, capabilities catalogue, journeys, roadmap honesty |

### Revision history

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 17 Sep 2026 | Initial client-ready product document |

---

## 1. Product overview

**DFS Corporate Portal** is the Bank / EMI’s digital channel for **corporate entity onboarding** and **post-activation corporate operations**. It is purpose-built for **entity accounts** (Merchant and Sub-merchant), not retail natural-person wallets.

### Value proposition

| For the Institution | For the Corporate Customer |
|---------------------|----------------------------|
| Framework-aligned digital CDD + Annex-C evidence | Guided remote onboarding with clear document lists |
| Tracking ID & 5-working-day decision target | Transparent status through to activation |
| Admin controls for sanctions, identity, docs | Single portal for finance views & network |
| Provisioning into DFS Agent stack | Franchise invites & commission plans |
| Maker–checker dual control | Role-based operators inside the company |

### Product pillars

1. **Onboard** — Entity KYC, documents, partner mobile KYC  
2. **Decide** — Compliance admin review & activation  
3. **Provision** — DFS corporate / agent account creation  
4. **Operate** — Balance, statement, cards, franchises, approvals  

---

## 2. Who it is for

| Segment | Product fit |
|---------|-------------|
| Bank / EMI launching or scaling corporate digital onboarding | Primary buyer / operator |
| Corporate Merchants (parent entities) | Primary end customer |
| Franchise / Sub-merchants | Network end customer |
| Partners in Partnership / LLP | Mobile KYC participants |
| Platform compliance & ops teams | Back-office users |

**Not for:** Retail consumer wallet applicants (out of product scope).

---

## 3. Product capabilities catalogue

### 3.1 Acquisition & onboarding

| Capability | Description | Status |
|------------|-------------|--------|
| Merchant self-signup | Email + OTP gated registration | Live |
| Franchise invite onboarding | Parent-issued token binds Sub-merchant | Live |
| Four entity types | Sole Prop, Small Business, Partnership, LLP | Live |
| Entity CDD capture | §E-style legal, tax, address, purpose fields | Live |
| Annex-C uploads | Type-specific mandatory packs | Live |
| Partner roster | Partnership / LLP partners invited to app KYC | Live |
| Mobile video + biometric KYC | OTP, video, CNIC, selfie, fingerprints | Live |
| Tracking ID & TAT due date | 5 working-day decision target | Live |
| 30-day draft resume | Incomplete files can continue | Live |

### 3.2 Compliance & activation

| Capability | Description | Status |
|------------|-------------|--------|
| Admin review queue | Pending cases with TAT visibility | Live |
| Sanctions workflow | Statusing with manual CLEAR path | Live (manual) |
| Identity workflow | Manual / waived until BV wired | Stub → **TBD** live BV |
| Document approve/reject | Per-document decision + download | Live |
| Approve / reject party | Written reject reason; activate + credentials | Live |
| Discrepancy notes | Ops communication to applicant | Live |
| DFS account provision | corporateonboarding on approve | Feature-flagged |

### 3.3 Day-2 corporate portal

| Capability | Description | Status |
|------------|-------------|--------|
| Dashboard | KYC, provision, finance & network snapshot | Live |
| Balance | Agent balance via Portal API | Feature-flagged |
| Mini-statement | Recent movements via Portal API | Feature-flagged |
| Cards | CMS search / inquiry / status | Feature-flagged |
| Franchise invites | Invite children + proposed commission % | Live |
| Franchise network | Confirm plans; view children | Live |
| Portal users & roles | PARTY_ADMIN, Maker, Checker, Approver, Releaser | Live |
| Approvals inbox | Dual-control request lifecycle | Live |

---

## 4. Customer journeys

### 4.1 Corporate Merchant — first time

```
Discover portal → Sign up → Verify email OTP → Complete entity profile
  → Add partners (if Partnership/LLP) → Upload Annex-C documents
  → Submit → Receive Tracking ID
  → Partners complete mobile KYC
  → Wait for admin decision (target 5 working days)
  → Receive activation credentials → Use dashboard & services
```

### 4.2 Partner — mobile KYC

```
Receive invite (phone + temporary PIN) → Open KYC app
  → Login → OTP → Set password → Complete video challenge
  → Capture CNIC, selfie, fingerprints → Submit → Done
```

### 4.3 Franchise expansion

```
Activated Merchant opens Invites → Sets commission % → Sends link
  → Sub-merchant registers → Completes onboarding & KYC
  → Admin activates → Parent confirms commission plan
  → Parent monitors network from Franchises
```

### 4.4 Dual-control action

```
Party Admin assigns roles → Maker raises request
  → Checker reviews → Approver (if required) → Releaser completes
```

---

## 5. Experience map (modules)

| Module | User feeling of “done” |
|--------|------------------------|
| Getting Started | Knows documents & eligibility before signup |
| Onboarding wizard | Entity file complete & submitted |
| Mobile KYC | Personal identity evidence captured |
| Admin | Case decided with audit fields populated |
| Dashboard | Sees activation & provision health at a glance |
| Balance / Statement | Trusts treasury visibility |
| Cards | Can locate & inquire corporate cards |
| Invites / Franchises | Network grows under controlled commission |
| Approvals | Sensitive actions cannot be solo-executed |

---

## 6. Compliance posture (client-facing honesty)

DFS Corporate Portal is designed against the **SBP Consolidated Customer Onboarding Framework** for **entity** remote onboarding (sections B, C, E, Annex-C, F, G, I, J as mapped in the BRD).

| Control area | Product behaviour today | Go-live dependency |
|--------------|-------------------------|--------------------|
| Entity CDD + Annex-C | Captured in portal | Client policy sign-off |
| Location evidence | IP + geo | Browser permission UX |
| Sanctions | Manual admin CLEAR | Automated vendor **TBD** |
| Identity BV/Verisys | Manual / waived path | NADRA integration **TBD** |
| TAT | Due date on case | Ops SLA process **TBD** |
| EDD | Risk rating + notes / video | Policy calibration **TBD** |

> This release is **not** a claim of full unsupervised production compliance until identity and sanctions vendors are live and client compliance has signed off.

---

## 7. Architecture (product view)

| Component | Role |
|-----------|------|
| DFS Corporate Portal (web) | Applicant & merchant experience; admin UI |
| DFS Corporate API | Orchestration, rules, storage, integrations |
| Partner KYC mobile app | Personal KYC capture |
| DFS Account API | Corporate account provisioning & LOVs |
| AgentApp Portal API | Balance, mini-statement, MPIN |
| CMS | Card lifecycle inquiries |
| MySQL | System of record |
| File store | KYC document & media storage |

---

## 8. Packaging & environments

| Item | Notes |
|------|-------|
| Deployment | Docker Compose sample (API + web + DB volume) |
| Configuration | Feature flags for Account / Portal / CMS APIs |
| Notifications | Email when enabled; otherwise secure ops logging in non-prod |
| Environments (dev/UAT/prod URLs) | **TBD** with client IT |
| Co-branding / white-label depth | Soft brands exist; full Bank brand pack **TBD** |

---

## 9. Roadmap themes (indicative)

| Theme | Intent | Status |
|-------|--------|--------|
| Live biometric identity (NADRA BV/Verisys) | Replace manual identity path | TBD |
| Automated sanctions screening | Replace manual CLEAR | TBD |
| Payment initiation behind approvals | Execute PAYMENT / BULK types | TBD |
| Hard multi-tenant / Bank isolation | If required by hosting model | TBD |
| Retention & records management | Regulatory retention pack | TBD |
| Expanded entity types beyond Annex-C 1–4 | If client policy expands | TBD |
| Formal accessibility & bilingual UX | Urdu / WCAG targets | TBD |

Prioritisation and commercials: **TBD** with Bank / EMI product committee.

---

## 10. Commercial model

| Element | Status |
|---------|--------|
| Licence / SaaS / transaction fees | TBD |
| Onboarding fee schedule | TBD |
| Franchise commission economics | Product stores **percentage plans** only; settlement **TBD** |
| Support tiers | TBD |

---

## 11. Competitive / positioning notes (for client discussion)

DFS Corporate Portal differentiates as a **single corporate stack**—onboarding + compliance decisioning + DFS provision + day-2 portal + franchise network—rather than a KYC form alone. Positioning vs other Bank channels or Paybridge-style products should be confirmed in client workshops (**TBD** messaging).

---

## 12. Demo script (suggested)

1. Land on Home — Merchant vs Sub-merchant story  
2. Sign up → OTP → Entity onboarding for Partnership  
3. Upload Annex-C sample pack → Submit → show Tracking ID  
4. Walk partner mobile KYC happy path (or recorded)  
5. Admin: review, clear sanctions, approve  
6. Merchant login: Dashboard → Balance/Statement (if APIs on)  
7. Create franchise invite → show network  
8. Create approval request → Checker/Releaser path  

---

## 13. Appendix — quick reference

| Item | Value |
|------|-------|
| Product name | DFS Corporate Portal |
| Entity types | Sole Prop, Small Business, Partnership, LLP |
| Party types | Merchant, Sub-merchant |
| Decision TAT target | 5 working days |
| Draft window | 30 days |
| Portal roles | Party Admin, Maker, Checker, Approver, Releaser |
| Companion specs | BRD · FSD |

---

*End of Product Document*
