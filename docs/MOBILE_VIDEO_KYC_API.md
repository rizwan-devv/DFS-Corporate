# Mobile app — video KYC (via OTP verify LOVs + upload)

**Base URL (server):** `http://46.224.146.158:8050`  
**Base URL (local):** `http://localhost:8090`  
**Prefix:** `/api/public/app-kyc`

---

## Flow

```text
login → otp/send → otp/verify  ← video text inside lovs.data.videoKyc
  → change-password → record video → video-verification → submit
```

---

## 1) Video text — inside `POST /otp/verify` response

No separate text API required. After OTP verify, read **`lovs.data.videoKyc`**.

**POST** `/api/public/app-kyc/otp/verify`  
**Header:** `Authorization: Bearer {sessionToken}`  
**Body:** `{ "code": "123456" }`

**Example response (relevant parts):**
```json
{
  "session": {
    "sessionToken": "...",
    "mobileVerified": true,
    "status": "KYC_IN_PROGRESS"
  },
  "lovs": {
    "responsecode": "000",
    "data": {
      "city": [ ... ],
      "province": [ ... ],
      "videoKyc": {
        "challengeId": "vc_abc123...",
        "scriptText": "My name is Hannan Ali. Today is 1 September 2026. I am opening a corporate account for Demo Traders.",
        "lines": [
          "My name is Hannan Ali.",
          "Today is 1 September 2026. I am opening a corporate account for Demo Traders."
        ],
        "durationSeconds": 10,
        "expiresAt": "2026-09-01T11:05:00Z",
        "templateId": "V01"
      }
    }
  }
}
```

| Field | Use |
|-------|-----|
| `lovs.data.city`, `province`, … | Dropdowns (DFS — unchanged) |
| `lovs.data.videoKyc.lines[0]`, `lines[1]` | Show **2 lines** on video screen |
| `lovs.data.videoKyc.scriptText` | Full text (alternative to lines) |
| `lovs.data.videoKyc.durationSeconds` | Timer **10 sec** |
| `lovs.data.videoKyc.challengeId` | Required for video upload |

**App:** front camera + mic, user reads text ~10 sec, record MP4.

**Retry if expired:** `POST /video-challenge` (optional fallback — same fields).

---

## 2) Upload recorded video

**POST** `/api/public/app-kyc/video-verification`  
**Content-Type:** `multipart/form-data`  
**Header:** `Authorization: Bearer {sessionToken}`

| Field | Required |
|-------|----------|
| `challengeId` | Yes — from `lovs.data.videoKyc.challengeId` |
| `video` | Yes — `.mp4` / `.webm` (max 50 MB) |
| `durationMs` | No |

```bash
curl -X POST "http://46.224.146.158:8050/api/public/app-kyc/video-verification" \
  -H "Authorization: Bearer SESSION_TOKEN" \
  -F "challengeId=vc_abc123" \
  -F "video=@read-aloud.mp4;type=video/mp4" \
  -F "durationMs=10000"
```

**Success:**
```json
{
  "session": {
    "videoVerificationStatus": "UPLOADED",
    "videoUploaded": true
  },
  "message": "Video received; complete CNIC/biometric submit next"
}
```

Then call existing **`POST /submit`** (CNIC front + back + selfie). Fingerprints are **not** uploaded — verify via NADRA and store `biometricRef` only.

---

## Summary for mobile

| Step | API | Video data |
|------|-----|------------|
| Text | `POST /otp/verify` | `lovs.data.videoKyc` |
| Upload | `POST /video-verification` | multipart `challengeId` + `video` |
| Retry text | `POST /video-challenge` | only if challenge expired |

Do **not** hardcode read-aloud text in the app.
