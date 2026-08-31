# DFS Corporate — Partner KYC (Vite + React + Capacitor)

Mobile app for partners invited after merchant portal submit.

## Run (browser — no Android Studio)

Backend must be on **8090**.

```powershell
cd D:\Karsaaz\DFS-Corporate\mobile
npm install
npm run dev
```

Open **http://localhost:5176**

- Login with **phone + PIN** from backend `[MAIL-DEV]` logs after portal submit  
- Or open invite URL: `http://localhost:5176/kyc?token=<appInviteToken>`

## Flow

1. CNIC fields (OCR stub / manual)  
2. Upload CNIC front, back, selfie  
3. Video + biometric **stubs**  
4. Submit → status `KYC_COMPLETED` → party advances when all partners done  

## Capacitor APK (optional)

```powershell
npm run cap:sync
npx cap add android   # once
npx cap open android  # Build APK
```

For a physical device, set `.env`:

```
VITE_API_BASE_URL=http://<your-pc-lan-ip>:8090
```

App id: `com.dfscorporate.kyc`
