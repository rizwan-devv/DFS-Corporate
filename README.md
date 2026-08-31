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
2. `POST /change-password` — force change (Bearer session)  
3. `POST /otp/send` → `POST /otp/verify` — mobile OTP  
4. KYC profile/docs/video/biometric  
5. `POST /submit` or `POST /complete` — marks KYC done; if **all** partners done → calls DFS backend account API  

Also: `GET /segments` proxies DFS backend `getAllSegments`.

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
