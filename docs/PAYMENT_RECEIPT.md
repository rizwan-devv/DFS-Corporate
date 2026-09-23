# Payment receipts

After a live FT / IBFT / UBP payment, the portal shows a **fintech receipt** modal.

## How to test

1. Deploy latest Prod (frontend + backend).
2. **UBP:** pay a known-good bill → receipt opens (amount, biller, consumer, STAN/RRN, portal ref).
3. **FT / IBFT:** Direct live pay → receipt opens.
4. **History:** tap any `LIVE-…` row under UBP history or Direct live pays → reopen receipt.
5. **Print:** use Print on the receipt (browser print dialog).

Failed payments also open a red **FAILED** receipt with DFS message.

Backend now returns `portalTxnRef` (e.g. `LIVE-000-B61C5D92`) on live pay responses so the receipt shows the ledger id immediately.
