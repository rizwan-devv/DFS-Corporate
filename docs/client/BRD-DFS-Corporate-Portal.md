# Business Requirements Document (BRD)

**Product:** DFS Corporate Portal  
**Audience:** Bank / EMI Client  
**Document ID:** DFS-CP-BRD-001  
**Version:** 1.0  
**Status:** Draft for Client Review  
**Date:** 17 September 2026  

---

## Document control

| Field | Value |
|-------|-------|
| Product name | DFS Corporate Portal |
| Document type | Business Requirements Document (BRD) |
| Classification | Client confidential |
| Prepared for | Bank / EMI stakeholders |
| Related docs | FSD (DFS-CP-FSD-001), Product Document (DFS-CP-PD-001) |

### Revision history

| Version | Date | Author | Notes |
|---------|------|--------|-------|
| 1.0 | 17 Sep 2026 | Delivery team | Initial client-ready draft from product codebase |

### Approvals (to be completed)

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Client business sponsor | TBD | | |
| Client compliance | TBD | | |
| Product owner | TBD | | |
| Delivery lead | TBD | | |

---

## 1. Executive summary

DFS Corporate Portal is a **standalone digital channel** for **corporate / entity** customer onboarding and day-2 operations for an EMI / digital financial services (DFS) environment. It enables remote entity KYC aligned to the **SBP Consolidated Customer Onboarding Framework**, admin compliance review, corporate account provisioning into the DFS Agent platform, and post-activation services (balance, statement, cards, franchise network, maker–checker approvals).

This BRD defines **why** the product exists, **who** it serves, **what** business outcomes are required, and **what is in / out of scope** for the current release presented to the Bank / EMI client.

> **Compliance note:** The current release is **framework-shaped and operational** for digital entity onboarding. Live NADRA Biometric/Verisys and automated sanctions-list vendor integrations are **stubbed / manual**. Full production compliance go-live is **TBD** pending client and vendor wiring.

---

## 2. Business context & problem statement

### 2.1 Context

Banks and EMIs must onboard **legal entities** (not only natural persons) through remote digital channels under SBP frameworks, with documented CDD, Annex-C evidence, sanctions/identity controls, TAT, and auditability. After activation, corporate customers need a controlled portal for treasury-style visibility and network (franchise / sub-merchant) management with dual-control workflows.

### 2.2 Problem

| Pain | Impact |
|------|--------|
| Manual / paper-heavy corporate onboarding | Slow TAT, inconsistent packs, high ops cost |
| Fragmented partner / beneficial-person KYC | Incomplete files, compliance risk |
| Weak digital audit trail (IP, geo, decisions) | Harder supervision & dispute handling |
| No unified post-activation corporate portal | Balance/statement/cards/network split across tools |
| Insufficient maker–checker for sensitive actions | Operational & fraud control gaps |

### 2.3 Solution summary

Provide one branded portal and supporting mobile KYC path that:

1. Captures entity CDD + Annex-C documents  
2. Collects partner personal KYC via mobile app where required  
3. Routes cases to platform admin for screening & decision  
4. Provisions DFS corporate / agent accounts on approval  
5. Offers activated merchants balance, statement, cards, franchise invites, and approval workflows  

---

## 3. Goals & success criteria

### 3.1 Business goals

| ID | Goal |
|----|------|
| BG-01 | Digitize corporate entity onboarding for Merchant and Sub-merchant parties |
| BG-02 | Align data & document capture to SBP Framework §E / Annex-C (entity types 1–4) |
| BG-03 | Enforce measurable decision TAT (**5 working days** target) with Tracking ID |
| BG-04 | Enable admin sanctions / identity / document decisioning before activation |
| BG-05 | Provision corporate accounts into DFS Agent stack after approval |
| BG-06 | Deliver post-activation self-service (balance, mini-statement, cards) |
| BG-07 | Support franchise / sub-merchant network with commission plans |
| BG-08 | Provide portal maker–checker–approver–releaser dual control |

### 3.2 Success criteria (acceptance-oriented)

| ID | Criterion | Measure |
|----|-----------|---------|
| SC-01 | Applicant can sign up, verify OTP, complete entity KYC & docs, submit | Tracking ID issued; status PENDING_APPROVAL when partner KYCs complete |
| SC-02 | Admin can approve/reject with written reason | Party ACTIVE or REJECTED; credentials emailed on approve |
| SC-03 | Partnership/LLP partners complete mobile KYC | All partners KYC_COMPLETED before pending approval |
| SC-04 | DFS account provision attempted on approve when integration enabled | Provision status SUCCESS / FAILED / PENDING visible |
| SC-05 | ACTIVE merchant can view balance & mini-statement when Portal API enabled | Data returned from AgentApp portal APIs |
| SC-06 | Merchant can invite sub-merchants and lock commission % | Franchise invite + commission plan lifecycle |
| SC-07 | Portal roles enforce approval steps | Request cannot skip required role gates |

**Commercial KPIs (conversion, fee income, SLA penalties):** TBD — client commercial model not in product.

---

## 4. Stakeholders & users

| Stakeholder | Interest |
|-------------|----------|
| Bank / EMI business | Corporate acquisition, network growth |
| Compliance / AML | CDD quality, screening, EDD, audit |
| Operations / back-office | Queue throughput, discrepancy handling |
| Corporate Merchant | Onboard once; manage franchises & finance views |
| Corporate Sub-merchant | Onboard under parent; operate wallet/account |
| Partners (Partnership/LLP) | Complete personal KYC via mobile |
| Platform Admin | Brand/party oversight, approve/reject, provision retry |
| IT / Integration | DFS Account, Portal, CMS APIs, security |

### Primary personas

| Persona | Needs |
|---------|-------|
| Corporate Merchant applicant | Guided onboarding, clear doc list, status tracking |
| Franchise / Sub-merchant | Invite-based signup, lighter parent-auth docs |
| Partner (natural person) | Mobile KYC: OTP, video, CNIC, biometrics |
| Platform Admin | Review queue, sanctions clear, doc approve/reject |
| Portal operator (Maker/Checker/…) | Dual-control approvals inside the party |

---

## 5. Scope

### 5.1 In scope (current release)

- Corporate **Merchant** self-signup and email OTP  
- **Franchise / Sub-merchant** onboarding via parent invite token  
- Entity types: Sole Proprietorship, Small Business, Partnership, LLP  
- Entity CDD fields (§E-style), IP + browser geo capture  
- Annex-C document upload packs by entity type  
- Partner roster + **mobile app KYC** (OTP, password, video challenge, CNIC, selfie, fingerprints)  
- Legacy token-based partner web KYC path (secondary)  
- Platform admin queue: sanctions, identity, risk/EDD notes, doc review, approve/reject, provision retry  
- Post-activation: dashboard, balance, mini-statement, cards (CMS), franchise invites/network, portal users, maker–checker approvals  
- Soft multi-brand support (`brands`)  

### 5.2 Out of scope (explicit)

| Item | Status |
|------|--------|
| Natural-person retail wallet onboarding (Framework §D) | Out |
| Live NADRA BV / Verisys APIs | Stub / manual — **TBD** for go-live |
| Automated UNSC/ATA sanctions vendor | Manual CLEAR — **TBD** |
| Full payment rails / e-money limit enforcement (EMI Regs) | Out / future |
| EMI licensing, capital, FPT for the EMI itself | Out |
| Pricing, tariffs, billing engine | **TBD** (not in product) |
| Hard multi-tenant isolation beyond brand_id | Limited — **TBD** if required |
| PCI DSS certification package | **TBD** |

---

## 6. Business requirements

### 6.1 Onboarding & KYC

| ID | Requirement | Priority |
|----|-------------|----------|
| BR-ONB-01 | System shall allow corporate Merchant signup with email verification (OTP) | Must |
| BR-ONB-02 | System shall collect entity CDD for Annex-C types 1–4 only | Must |
| BR-ONB-03 | System shall require Annex-C document packs appropriate to entity type | Must |
| BR-ONB-04 | Partnership/LLP shall require partner roster and individual mobile KYC completion before pending approval | Must |
| BR-ONB-05 | Sole Prop / Small Business shall treat applicant as owner; owner completes mobile KYC | Must |
| BR-ONB-06 | On submit, system shall issue Tracking ID and compute decision due date (5 working days) | Must |
| BR-ONB-07 | Draft applications shall remain resumable for 30 days | Must |
| BR-ONB-08 | System shall capture IP and optional browser geolocation for remote onboarding evidence | Must |
| BR-ONB-09 | HIGH risk shall support EDD notes / video KYC reference | Should |
| BR-ONB-10 | Sub-merchant onboarding shall bind to parent Merchant via franchise invite | Must |

### 6.2 Compliance & admin decisioning

| ID | Requirement | Priority |
|----|-------------|----------|
| BR-CMP-01 | Admin shall review pending parties with Tracking ID and TAT due date | Must |
| BR-CMP-02 | Admin shall set sanctions status (manual CLEAR / HIT path) | Must |
| BR-CMP-03 | Admin shall manage identity verification status (manual / waived path until BV live) | Must |
| BR-CMP-04 | Admin shall approve or reject with written reject reason to applicant | Must |
| BR-CMP-05 | Admin shall approve/reject individual documents and download evidence | Must |
| BR-CMP-06 | Admin shall record discrepancy notes to applicant | Should |
| BR-CMP-07 | On approve, party becomes ACTIVE and login credentials are communicated | Must |
| BR-CMP-08 | On approve, system shall attempt DFS corporate account provisioning when integration enabled | Must |

### 6.3 Post-activation portal

| ID | Requirement | Priority |
|----|-------------|----------|
| BR-PRT-01 | ACTIVE parties shall access dashboard with KYC/provision status and finance snapshot | Must |
| BR-PRT-02 | Merchant shall view agent balance and mini-statement via DFS Portal API when enabled | Must |
| BR-PRT-03 | Merchant shall manage franchise invites and commission % plans | Must |
| BR-PRT-04 | Merchant shall view child network and (where enabled) child balance/statement | Should |
| BR-PRT-05 | Merchant shall search/inquire cards via CMS integration when enabled | Should |
| BR-PRT-06 | Party admin shall invite portal users and assign Maker/Checker/Approver/Releaser roles | Must |
| BR-PRT-07 | Approval workflow shall enforce MAKER → CHECKER → APPROVER → RELEASER sequence (with defined skip rules) | Must |

### 6.4 Non-functional (business view)

| ID | Requirement | Priority |
|----|-------------|----------|
| BR-NFR-01 | Access shall be authenticated (JWT) with role separation for platform admin | Must |
| BR-NFR-02 | Document uploads shall support large KYC media (configured multipart limits) | Must |
| BR-NFR-03 | Notifications (OTP, invites, credentials) shall be email-based when mail enabled | Must |
| BR-NFR-04 | Availability / RTO / RPO / retention / encryption-at-rest policies | **TBD** with client IT |
| BR-NFR-05 | Production SLA (uptime, support hours, severity matrix) | **TBD** |

---

## 7. Business processes (high level)

### 7.1 Corporate Merchant onboarding

```
Signup → Email OTP → Login → Entity CDD → Partners (if required) → Annex-C docs
  → Submit (Tracking ID + TAT) → Partner mobile KYCs complete
  → PENDING_APPROVAL → Admin review → Approve / Reject
  → ACTIVE + credentials → DFS provision (if enabled)
```

### 7.2 Franchise / Sub-merchant

```
Parent Merchant creates invite (+ commission %) → Child opens invite link
  → Signup as SUB_MERCHANT → OTP → Onboarding (incl. parent authority docs)
  → Same KYC / admin / provision path → Parent confirms commission plan
```

### 7.3 Partner mobile KYC

```
Invite (phone + temp PIN) → App login → OTP → Change password
  → Video challenge/verification → Submit CNIC + selfie + fingerprints
  → KYC_COMPLETED
```

### 7.4 Maker–checker

```
PARTY_ADMIN assigns roles → MAKER creates request
  → CHECKER → APPROVER (optional skip if checker also Approver) → RELEASER → Done
```

---

## 8. Regulatory & compliance mapping (business)

Primary reference: **SBP Consolidated Customer Onboarding Framework**.

| Framework area | Business intent in portal | Current delivery state |
|----------------|---------------------------|------------------------|
| §B Applicability | EMI / digital RE remote channel | Implemented as digital portal |
| §C Remote onboarding | Web + mobile KYC | Implemented |
| §E Entity opening | Entity fields + associated persons | Implemented for types 1–4 |
| Annex-C | Document packs | Implemented |
| §F.1 Identity | Identity status workflow | Manual / waived — **BV TBD** |
| §F.3 Location | IP + geo | Implemented |
| §F.4 Sanctions | Screening status workflow | Manual CLEAR — **vendor TBD** |
| §G EDD | Risk rating + notes / video | Partially implemented |
| §I TAT | 5 working days + Tracking ID | Due date captured; enforcement ops process **TBD** |
| §J Facilitation | 30-day draft resume | Implemented |

EMI Regulations (2019/2023) inform context; wallet limits / full payment controls remain out of scope.

---

## 9. Assumptions & dependencies

| ID | Assumption / dependency |
|----|-------------------------|
| AS-01 | Client operates as EMI / DFS provider with authority to onboard corporate entities |
| AS-02 | DFS Account API, AgentApp Portal API, and CMS endpoints are available in target environments |
| AS-03 | Email (or approved notification channel) is available in production |
| AS-04 | Client provides branding, legal entity name, and customer T&Cs text | **TBD** |
| AS-05 | Client compliance signs off stub vs live screening/BV before go-live | **TBD** |
| AS-06 | Commission commercial terms are configured operationally (no billing engine) | **TBD** |

---

## 10. Risks & open questions

| ID | Item | Type |
|----|------|------|
| RQ-01 | Final legal product name / logos for Bank co-brand | TBD |
| RQ-02 | Production NADRA / Verisys vendor contract & SLA | TBD |
| RQ-03 | Automated sanctions list vendor | TBD |
| RQ-04 | Data retention, purge, encryption-at-rest standards | TBD |
| RQ-05 | Whether payment approval types will execute real payments in a later phase | TBD |
| RQ-06 | UAT exit criteria & regulatory examination pack | TBD |
| RQ-07 | Support model, environments, DR | TBD |

---

## 11. Timeline & commercial (placeholder)

| Item | Status |
|------|--------|
| Implementation phases / milestones | TBD with client PMO |
| Licence / subscription / transaction fees | TBD |
| Commission sharing commercial schedule | TBD (product stores % plans only) |

---

## 12. Glossary

| Term | Meaning |
|------|---------|
| DFS | Digital Financial Services / Agent platform integration layer |
| EMI | Electronic Money Institution |
| CDD | Customer Due Diligence |
| EDD | Enhanced Due Diligence |
| Annex-C | SBP document checklist for entity types |
| Merchant | Parent corporate party |
| Sub-merchant | Child / franchise corporate party |
| Tracking ID | Unique case reference after submission |
| TAT | Turnaround time for onboarding decision |
| Maker–Checker | Dual-control approval workflow |

---

## 13. Document close

This BRD is the business baseline for **DFS Corporate Portal** as presented to the Bank / EMI client. Detailed behaviours, screens, APIs, and data are specified in **FSD DFS-CP-FSD-001**. Product positioning and feature catalogue are in **Product Document DFS-CP-PD-001**.

*End of BRD*
