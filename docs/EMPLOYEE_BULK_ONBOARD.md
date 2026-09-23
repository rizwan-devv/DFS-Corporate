# Employee bulk onboarding (park → DFS confirm)

Corporate uploads a staff CSV. The portal validates and **parks** each row on DFS (stub until the real API exists). When DFS creates the consumer account, it calls our confirm webhook (or you simulate from the UI). Status becomes **OPEN**.

## Portal

**Network → Employees** (`/employees`)

1. Download CSV template  
2. Upload (Excel → save as CSV)  
3. Review VALIDATED / INVALID rows  
4. **Park on DFS**  
5. Wait for confirmation → **OPEN** (or use **Simulate OPEN** while stubbed)

## CSV columns

```csv
employee_code,full_name,father_name,mobile,cnic,date_of_birth,gender,email,department
E001,Ali Khan,Ahmed Khan,03005900256,3520212345671,1990-01-15,M,ali@example.com,Finance
```

- `full_name`, `mobile` (10+ digits), `cnic` (13 digits) required  
- Max **500** rows per file  
- Duplicate mobile/CNIC in the same file → INVALID  

## Statuses

| Batch | Meaning |
|-------|---------|
| DRAFT | Uploaded, not parked |
| PARKED | Sent to DFS (awaiting accounts) |
| PARTIAL / COMPLETED / FAILED | After confirmations |

| Row | Meaning |
|-----|---------|
| INVALID | Bad data |
| VALIDATED | Ready to park |
| PARKED | On DFS side |
| OPEN | Account confirmed |
| FAILED / REJECTED | Confirm said no |

## APIs

Authenticated (JWT):

| Method | Path |
|--------|------|
| GET | `/api/employees/bulk/template.csv` |
| POST | `/api/employees/bulk` (multipart `file`) |
| POST | `/api/employees/bulk/{publicId}/park` |
| GET | `/api/employees/bulk` |
| GET | `/api/employees/bulk/{publicId}` |
| GET | `/api/employees/bulk/{publicId}/result.csv` |
| POST | `/api/employees/bulk/confirm` (portal simulate) |

DFS webhook (public + key):

```http
POST /api/public/employee-onboard/confirm
X-Employee-Onboard-Key: <dfs.employee-onboard.confirm-key>
Content-Type: application/json

{
  "rowPublicId": "<uuid from park/result>",
  "status": "OPEN",
  "dfsAccountNo": "03005900256",
  "dfsCustomerId": "324",
  "message": "Account created"
}
```

`status`: `OPEN` | `FAILED` | `REJECTED` (also accepts `SUCCESS` / `CREATED` as OPEN).  
You may send `parkRef` instead of `rowPublicId`.

## Config

```yaml
dfs:
  employee-onboard:
    use-stub: true   # StubEmployeeAccountParkClient
    confirm-key: ${DFS_EMPLOYEE_ONBOARD_CONFIRM_KEY:dev-employee-onboard-key}
```

When DFS provides the real park API, add an `EmployeeAccountParkClient` implementation and set `use-stub: false`.

## Out of scope (this MVP)

- Live DFS park HTTP call  
- Consumer-app KYC inside the corporate portal  
- Auto salary / FT to new wallets (use existing FT bulk after OPEN)
