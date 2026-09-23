# Live bulk transfers (FT / IBFT / UBP)

Corporate portal can run **many** live DFS transfers from one CSV without a DFS bulk API. Each row uses the **same single-rail APIs** already used for one-off FT, IBFT, and UBP.

Single and bulk stay separate:

| Mode | UI tab | API prefix |
|------|--------|------------|
| Single FT / IBFT / UBP | **Single FT** / **Single IBFT** / **Single UBP** | `/api/transfers/live/ft/*`, `/ibft/*`, `/ubp/*` |
| Live bulk | **Bulk CSV (live)** on the same product page | `/api/transfers/live/bulk/*` |
| Mock bulk (live off) | **Bulk CSV (mock)** | `/api/transfers/mock/bulk` |

Raast has no live bulk.

## Prerequisites

- `DFS_PORTAL_API_ENABLED=true`
- `CORPORATE_PORTAL_API_KEY` set (X-Portal-Key)
- Party **ACTIVE** with payer identity that already works for **single** live FT/IBFT/UBP
- FT bulk: party must have valid CNIC + `dfs_app_user_id`; one **customer MPIN** for the whole batch (held in memory only, never stored)

## Flow

1. Download CSV template for the product  
2. Upload CSV → batch status `DRAFT`  
3. **Start** (FT requires MPIN) → `QUEUED` → async worker → `RUNNING`  
4. Worker calls existing `LiveTransferService` once per row (≈350 ms gap)  
5. Batch ends `COMPLETED` / `PARTIAL` / `FAILED`  
6. Download result CSV  

Max **100** rows per file.

Per-row DFS steps:

- **FT** — initiate → confirm (same MPIN)  
- **IBFT** — title fetch → advice  
- **UBP** — bill inquiry → pay (CSV amount optional; inquiry amount used if blank)  

Success = DFS `responsecode` `000`.

## CSV templates

### FT

```csv
beneficiary_mobile,amount,narration
03006088659,10,Bulk FT sample
```

### IBFT

```csv
beneficiary_account_no,bank_imd,amount,purpose,narration
PK00XXXX0000000000000000,627271,10,Payment,Bulk IBFT sample
```

### UBP

```csv
utility_company_code,consumer_no,amount
SNGPL001,1234567890,100
```

Header row optional if columns match order. Separators: `,` `;` or tab.

## API (authenticated Bearer JWT)

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/transfers/live/bulk/template.csv?productType=FT\|IBFT\|UBP` | Template download |
| POST | `/api/transfers/live/bulk` | multipart: `productType`, `file` |
| POST | `/api/transfers/live/bulk/{publicId}/start` | body `{ "mpin": "…" }` for FT |
| GET | `/api/transfers/live/bulk/{publicId}` | Batch + rows |
| GET | `/api/transfers/live/bulk?productType=FT` | Recent batches |
| GET | `/api/transfers/live/bulk/{publicId}/result.csv` | Result export |

Single endpoints are unchanged and never go through the bulk tables.

## UI

On **FT**, **IBFT**, or **UBP** transfer pages (live mode on):

1. Tab **Single …** — existing one-off live form  
2. Tab **Bulk CSV (live)** — upload → start → poll → result CSV  

Mock mode keeps **Bulk CSV (mock)** on `/api/transfers/mock/bulk`.

## Ops / limits

- Flyway: `V24__live_bulk_transfers.sql` (`live_bulk_batches`, `live_bulk_rows`)  
- `@EnableAsync` + `LiveBulkTransferWorker` (separate bean so async works)  
- Restart / scale-out drops in-memory FT MPIN for in-flight batches — restart Start if needed  
- Partial success is normal: some rows `000`, others fail; fix CSV and upload a new batch for failures  

## Quick test

1. Confirm **single** live FT/IBFT/UBP works for the party  
2. Open the same product → **Bulk CSV (live)** → download template → 1–2 known-good rows  
3. Upload → Start (MPIN for FT) → wait until status terminal → check row codes and result CSV  
