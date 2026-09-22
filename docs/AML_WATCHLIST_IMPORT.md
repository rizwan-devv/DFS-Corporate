# AML watchlist — official OFAC / UN import

Local screening uses table `aml_watchlist` (CNIC exact + fuzzy name). Official rows are **downloaded and parsed** from published files — not scraped from HTML.

## Sources

| Source | Format | Default URL |
|--------|--------|-------------|
| OFAC SDN | CSV (no header) | `https://sanctionslistservice.ofac.treas.gov/api/download/SDN.CSV` |
| OFAC ALT | CSV aliases | `https://sanctionslistservice.ofac.treas.gov/api/download/ALT.CSV` |
| UN Security Council | Consolidated XML | `https://scsanctions.un.org/resources/xml/en/consolidated.xml` |

Requests send a real `User-Agent` (OFAC rejects bare clients).

## Behaviour

1. Deactivate prior **OFAC** and **UN** rows.
2. Insert new active rows from the files.
3. Leave **INTERNAL** / **NACTA** rows alone (demo CNICs, local seeds).
4. Does **not** automatically re-screen parties — use **Re-screen AML** on a case, or rely on gates at submit / provision.

## When it runs

- **Weekly cron** (default Sunday 02:00 server time): `aml.import.cron`
- **Manual**: `POST /api/admin/aml/watchlist/refresh` (PLATFORM_ADMIN) or Admin UI **Refresh AML lists**

Disable the scheduler with `AML_IMPORT_SCHEDULE_ENABLED=false`.

## Config (`application.yml` / env)

| Property / env | Default | Meaning |
|----------------|---------|---------|
| `aml.import.schedule-enabled` / `AML_IMPORT_SCHEDULE_ENABLED` | `true` | Weekly job |
| `aml.import.cron` / `AML_IMPORT_CRON` | `0 0 2 * * SUN` | Cron |
| `aml.import.ofac-enabled` / `AML_IMPORT_OFAC_ENABLED` | `true` | Import OFAC |
| `aml.import.un-enabled` / `AML_IMPORT_UN_ENABLED` | `true` | Import UN |
| `aml.import.max-names` / `AML_IMPORT_MAX_NAMES` | `50000` | Cap per file |
| `aml.fuzzy-threshold` / `AML_FUZZY_THRESHOLD` | `88` | Name match → MANUAL_REVIEW |

## Manual refresh response (example)

```json
{
  "sourceVersion": "import-20260322-210000",
  "deactivatedPriorOfacUn": 12000,
  "ofacPrimaryNames": 14000,
  "ofacAliasNames": 8000,
  "unNames": 900,
  "activeTotal": 23010,
  "activeInternal": 5,
  "errors": [],
  "elapsedMs": 45000,
  "message": "Official lists refreshed (INTERNAL/NACTA demo rows kept)"
}
```

## Ops notes

- First import can take 1–2 minutes and needs outbound HTTPS from the backend host.
- OFAC names rarely include Pakistani CNICs — demo HIT-by-CNIC still comes from **INTERNAL** seeds; fuzzy name hits can come from OFAC/UN.
- CSV bulk upload of custom rows remains: `POST /api/admin/aml/watchlist/csv` (`text/plain`).
