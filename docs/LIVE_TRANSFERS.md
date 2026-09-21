# Live DFS transfers (FT / IBFT / UBP)

Local guide — do **not** commit secrets. Portal key stays server-side only.

## Architecture

```
Browser (JWT) → DFS Corporate Backend → DFS services (X-Portal-Key)
                                      ├─ agentapp  (balance — already done)
                                      ├─ app       (MPIN verify)
                                      └─ transactions (FT / IBFT / UBP)
```

| Product | Downstream steps (Postman) | Our portal API |
|---------|----------------------------|----------------|
| **IBFT** | POST bankList → titleFetch → advice | `GET /ibft/banks` (wraps POST) → `POST /ibft/title-fetch` → `POST /ibft/advice` |
| **FT** | initiateLocalFT → fundsTransferLocal | `POST /ft/initiate` → `POST /ft/confirm` |
| **UBP** | GET getbiller → billInquiry → billPayment | `GET /ubp/billers` → `POST /ubp/inquiry` → `POST /ubp/pay` |
| **Raast** | *not in Postman* | Still mock only |

**Bank list:** DFS exposes **POST** `/v1/corporate/ibft/bankList` (empty payload), not GET. The portal offers `GET /api/transfers/live/ibft/banks` for a clean UI; the backend still calls POST upstream.

Always branch on `responsecode === "000"` (HTTP 200 can still be a business failure).

## Enable locally

`backend/src/main/resources/application.yml` / env:

```bash
DFS_PORTAL_API_ENABLED=true
CORPORATE_PORTAL_API_KEY=<real key from DFS .env>
DFS_TXN_API_BASE_URL=http://46.225.160.93:18009/transactions
DFS_APP_API_BASE_URL=http://46.225.160.93:18002/app
DFS_PORTAL_API_CHANNEL=MOB
```

Restart the Spring Boot process after changing env.

## Party prerequisites

Logged-in corporate must be **ACTIVE** with:

| Field | Used as | Required for |
|-------|---------|--------------|
| `parties.phone` | `fromAccountNo` / `mobileNumber` | All |
| `parties.cnic_number` | `fromAccountNid` / `nidNo` | IBFT advice, UBP pay, FT |
| `parties.dfs_app_user_id` | payer `appUserId` | FT confirm (or pass in UI) |
| `parties.level_code` | balance lookups | Balance (existing) |

SQL example:

```sql
UPDATE parties
SET dfs_app_user_id = '326'   -- DFS APP_USER_ID, not local partner_app_users.id
WHERE id = <your_party_id>;
```

## Testing steps

1. Login as ACTIVE merchant → open **Transfers** sidebar → expand.
2. Call status (or look at the green/blue banner on FT/IBFT/UBP):

   `GET /api/transfers/live/status` with `Authorization: Bearer <jwt>`

   Expect `liveEnabled: true`.
3. **IBFT**
   - Open Interbank Transfer — banks load from live list.
   - Pick bank, enter IBAN/account + amount → **Fetch title**.
   - Confirm name → **Submit advice** (moves money — use tiny amount on test wallets).
4. **FT**
   - Enter beneficiary wallet mobile + amount → **Initiate FT**.
   - Enter customer **MPIN** (+ APP_USER_ID if party field empty) → **Confirm FT**.
5. **UBP**
   - Pick live biller → consumer no → **Bill inquiry** → **Pay bill**.
6. **Raast** — still mock (no corporate API in collection).
7. With `DFS_PORTAL_API_ENABLED=false`, UI falls back to **mock** flows.

### Postman smoke (optional)

Use `docs/DFS_Corporate_Portal.postman_collection (2).json` with environment `portalKey`. Confirm bankList / getbiller / initiateLocalFT before portal UI testing.

### Failure codes (typical)

| Code | Meaning |
|------|---------|
| 000 | Success |
| 406 | Invalid / missing portal key |
| 113 | Wrong MPIN (FT) |
| 127 | Wrong MPIN (verify endpoint) |
| 125 | Customer not found |

## Security notes

- Never put `CORPORATE_PORTAL_API_KEY` in the frontend or commit it.
- MPIN is POSTed only to our backend; we do not log it.
- Do not send `type=QR` on initiateLocalFT from portal (needs mobile OTP token).
