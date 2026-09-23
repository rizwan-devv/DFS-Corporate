# Employee bulk onboarding (DFS bulkAccounts)

Corporate uploads a staff CSV. On **Park on DFS**, each valid row calls:

`POST {DFS_APP_API_BASE_URL}/v1/corporate/bulkAccounts`  
Header: `X-Portal-Key` (= `CORPORATE_PORTAL_API_KEY`)

```json
{
  "channel": "COP",
  "segment": "Corporate Clients Segment",
  "payload": {
    "mobileNo": "03001111111",
    "nidNo": "3520100000001",
    "accountTitle": "Probe One"
  }
}
```

Mapped from CSV: `mobile` → `mobileNo`, `cnic` → `nidNo`, `full_name` → `accountTitle`.

## Portal

**Network → Employees** (`/employees`)

1. Download CSV template  
2. Upload  
3. Review VALIDATED / INVALID  
4. **Park on DFS** → live `bulkAccounts` per row  
5. Row becomes **OPEN** if DFS returns an account, else **PARKED** (use Simulate OPEN / webhook if async)

## CSV columns

```csv
employee_code,full_name,father_name,mobile,cnic,date_of_birth,gender,email,department
E001,Ali Khan,Ahmed Khan,03005900256,3520212345671,1990-01-15,M,ali@example.com,Finance
```

Required for DFS: `full_name`, `mobile` (10+ digits), `cnic` (13 digits).

## Config

```yaml
dfs:
  portal-api:
    enabled: true   # DFS_PORTAL_API_ENABLED
    portal-key: …   # CORPORATE_PORTAL_API_KEY
    app-base-url: http://46.225.160.93:18002/app
  employee-onboard:
    use-stub: false   # DFS_EMPLOYEE_ONBOARD_USE_STUB
    channel: COP
    segment: Corporate Clients Segment
    bulk-accounts-path: /v1/corporate/bulkAccounts
    confirm-key: …    # webhook X-Employee-Onboard-Key
```

## Statuses

| Row | Meaning |
|-----|---------|
| VALIDATED | Ready to park |
| PARKED | DFS accepted; account not yet in response |
| OPEN | Account number known (from DFS response or confirm) |
| FAILED | DFS / validation error |

## Confirm webhook (optional)

```http
POST /api/public/employee-onboard/confirm
X-Employee-Onboard-Key: <confirm-key>
```

Body: `{ "rowPublicId", "status": "OPEN", "dfsAccountNo", "dfsCustomerId", "message" }`

Portal also has **Simulate OPEN** for PARKED rows while testing.
