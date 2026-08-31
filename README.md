# DFS Corporate

Standalone **entity / corporate** digital onboarding (not Paybridge).

> Full product & KYC explanation: see **[PROJECT_AND_KYC.md](./PROJECT_AND_KYC.md)**  
> MySQL: see **[MYSQL_SETUP.md](./MYSQL_SETUP.md)**

## Summary
- Backend: Spring Boot 3.3 · Java 21 · port **8090** (default H2; MySQL via profile)
- Frontend: Vite + React · port **5173**
- **KYC app**: partner mobile KYC — deep link `dfscorporate://kyc?token=`
- **DFS backend account API**: `POST /agentapp/v1/corporateonboarding` when all partner KYCs complete

## KYC app API sequence (`/api/public/app-kyc`)
1. `POST /login` — phone+PIN **or** email+password  
2. `POST /otp/send` → `POST /otp/verify` — OTP first (no password change required); on verify success returns DFS `getAllLovs` as `lovs`  
3. `POST /change-password` — after OTP (temp PIN → password); password kept for DFS Account API (plain) until admin approve  
4. **`POST /submit` (multipart)** — single call with profile fields + CNIC front/back + selfie + 8 fingers → `KYC_COMPLETED` (requires mobile verified)  
5. When **all** partners done → party `PENDING_APPROVAL`  
6. **Admin approve** → DFS `corporateonboarding` → agent app login  

Portal = business docs. App = one submit with KYC media.  
Postman: `postman/DFS-Corporate-KYC-App-Slim.postman_collection.json`  
Optional: `GET /lovs` refreshes DFS getAllLovs without re-OTP.

## Enable DFS backend account API
```powershell
$env:DFS_ACCOUNT_API_ENABLED="true"
$env:DFS_ACCOUNT_API_BASE_URL="http://46.225.160.93:18001"
cd backend
mvn spring-boot:run
```

Default `enabled=false` leaves provision status **PENDING** (safe for local).

## Quick start
```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

**Default DB = MySQL** (`dfs_corporate` / user `dfs` / password `change-me`).  
Optional H2: `mvn spring-boot:run "-Dspring-boot.run.profiles=h2"`

Admin: `admin@dfscorporate.local` / `Admin@123`
