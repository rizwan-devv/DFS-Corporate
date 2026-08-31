# DFS Corporate — Project & KYC Guide

## 1. What is this project?

**DFS Corporate** is a **standalone** digital onboarding product (separate from Paybridge) for **entity / corporate accounts only**.

| Item | Detail |
|------|--------|
| Location | `d:\Karsaaz\DFS-Corporate\` |
| Backend | Spring Boot 3.3 · Java 21 · JWT · Flyway · H2 (dev) · port **8090** |
| Frontend | Vite + React + TypeScript · DFS Connect dark theme · port **5173/5174** |
| Audience | Corporate Merchant & Corporate Sub-merchant applicants + platform admin |
| Paybridge | **Reference only — not modified** |

### What it does
1. Applicant signs up (entity account type)  
2. Verifies email with OTP (2FA)  
3. Completes **entity KYC** per SBP Consolidated Customer Onboarding Framework  
4. Uploads **Annex-C** documents  
5. Submits → gets **Tracking ID** · **5 working-day TAT**  
6. Admin reviews (sanctions / identity / docs) → approve → login credentials emailed  

### What it does **not** do (yet)
- Natural-person retail wallets (Framework §D)  
- Live NADRA Biometric / Verisys APIs (stubbed for manual/admin)  
- Automated UNSC/ATA list vendor (manual CLEAR by admin)  
- Payments / e-money wallet limits (EMI Regs §14)  
- EMI licensing / capital / FPT for the EMI itself  

---

## 2. Which regulations / KYC framework?

Primary: **SBP Consolidated Customer Onboarding Framework**

| Section | How DFS Corporate uses it |
|---------|---------------------------|
| **§B** Applicability | EMIs / digital REs — remote digital channel |
| **§C** Remote onboarding | Website portal; CNIC/NICOP/POC/POR/ARC holders for persons |
| **§E** Entity opening | Full Table-B style entity fields + associated persons |
| **Annex-C** | Document packs for **4 entity types only**: Sole Prop, Small Business, Partnership, LLP |
| **§F.1** Identity verification | Status workflow: PENDING → DEBIT_BLOCKED → admin WAIVED_MANUAL / future BV |
| **§F.3** Location | Captures **IP** + browser **geo** (if allowed) |
| **§F.4** Sanctions | Pre-screening status PENDING → MANUAL_REVIEW → admin CLEAR / HIT |
| **§G** EDD | Risk rating LOW/MEDIUM/HIGH; HIGH requires video KYC ref or EDD notes |
| **§I** TAT | Entity decision due in **5 working days**; Tracking ID; reject reason in writing |
| **§J** Facilitation | Draft resume window **30 days**; step progress pills |

Also informed by **EMI Regulations (2019/2023)** for EMI context (wallet limits still out of scope).

---

## 3. End-to-end applicant journey

```
Signup → OTP → Entity form (§E) → Persons / Partner invites → Annex-C docs
    → EDD (if HIGH) → Submit (Tracking ID + TAT) → Admin decision → Login
```

### Partner KYC links (Partnership / LLP)
1. Lead selects **Partnership** or **LLP** and saves entity details.  
2. Lead invites each partner (name + email) → system creates `associated_persons` + `partner_invites` with a public token.  
3. Email/log shows link: `{frontend}/partner-kyc/{token}` (14-day expiry).  
4. Partner opens link (**no login**) → fills CDD + uploads ID front/back + live photo → **COMPLETED**.  
5. Lead uploads **firm** docs (deed, authority, registration/SECP).  
6. Submit is allowed only when **all** active partner invites are COMPLETED.  

### Statuses
`DRAFT` → `PENDING_APPROVAL` → `ACTIVE` | `REJECTED`

### Seed admin
`admin@dfscorporate.local` / `Admin@123`

### Local mail
`app.mail.enabled=false` → OTP, passwords, and **partner KYC links** appear in **backend logs**.

---

## 4. Entity KYC data collected (§E)

### Entity types in scope
1. Sole Proprietorship  
2. Small business / freelance  
3. Partnership (optional “unregistered” flag → skips Registrar cert)  
4. LLP  

### Entity
- Legal name, entity type (Annex-C 1–4)  
- Incorporation: number, date, country, authority  
- Tax: NTN, tax country, FATCA/CRS  
- Addresses: registered, mailing, place of business + reason if different  
- Nature of business, purpose of account, intended relationship  
- Terms acceptance  

### Associated natural persons
- Sole / small business: add persons on the form (operators need mother’s maiden name, place of birth, DOB).  
- Partnership / LLP: partners complete CDD via **invite link** (not only on the lead form).  

### Documents (Annex-C driven)
- Sole / small: ID front/back/photo + at least one alternative (NTN / trade body / letterhead / etc.).  
- Partnership: deed + authority (+ registration cert unless unregistered).  
- LLP: deed + SECP cert + authority.  
- Partner personal IDs attach via invite uploads (`PARTNER_{id}_*`).

---

## 5. Admin controls

- Pending queue with Tracking ID + TAT due date  
- View persons + documents + IP/geo + sanctions/identity/risk  
- Mark sanctions CLEAR  
- Approve (activates account + emails password)  
- Reject with written reason  
- Discrepancy note API (emails applicant)  

---

## 6. Run locally

```bash
cd d:\Karsaaz\DFS-Corporate\backend
mvn spring-boot:run

cd d:\Karsaaz\DFS-Corporate\frontend
npm run dev
```

Use a **new email** for each test signup after schema upgrades.

---

## 7. Compliance honesty

This release is **framework-shaped and operational for digital entity onboarding**, with **stubs** where NADRA/screening vendors are required. It is **not** a claim of full production go-live compliance until BV/Verisys and automated sanctions are wired.
