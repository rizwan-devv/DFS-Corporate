# Payment approval workflow — amount routing

## Rules (default)

| Amount (PKR) | After Maker submit | Then |
|--------------|--------------------|------|
| **≤ 5,000** | Goes to **RELEASER** | Releaser Releases → `APPROVED` (payment authorized) |
| **> 5,000** | Goes to **CHECKER** | Checker → Approver → Releaser → `APPROVED` |

- Threshold env: `APPROVAL_DIRECT_RELEASE_MAX` (default `5000`) in `application.yml` → `approvals.payment.direct-release-max-amount`
- **No DFS money move until Releaser** (Release button). Live DFS hook on Release can be added next.
- Party must have portal users covering required roles (or `PARTY_ADMIN` which can act as any step):
  - ≤ 5k: at least **RELEASER** (or admin)
  - > 5k: **CHECKER**, **APPROVER**, and **RELEASER** (or admin)
- Maker cannot check or release their own request (unless `PARTY_ADMIN`)

## Test guide

### 0. Prep users (PARTY_ADMIN → Users)

Create (or assign) three users on the ACTIVE merchant:

| User | Roles |
|------|--------|
| maker@… | `MAKER` |
| checker@… | `CHECKER` (optional: also `APPROVER` to skip Approver step) |
| releaser@… | `RELEASER` |

Owner often already has all roles — fine for a quick solo test.

### 1. Low amount → direct Releaser

1. Login as **Maker**
2. Transfers → FT or IBFT → **New Request**
3. Amount **5000** (or less) → **Submit**
4. **All Requests**: status **AUTHORIZED** (awaiting releaser), step `RELEASER`
5. Login as **Releaser** → **Release**
6. Status becomes completed / `APPROVED` in Details

### 2. High amount → full chain

1. Maker submits amount **10000** (or any &gt; 5000)
2. All Requests: **PENDING_CHECK** / step `CHECKER`
3. Checker → **Approve** → goes to Approver (or Releaser if checker also has Approver)
4. Approver → **Approve** → Releaser
5. Releaser → **Release** → done

### 3. Missing roles

Submit &gt; 5k with no Checker/Approver/Releaser users on the party → API error listing which role is missing.

### 4. Stop

Any allowed actor on an in-progress item → **Stop** → rejected / STOPPED.
