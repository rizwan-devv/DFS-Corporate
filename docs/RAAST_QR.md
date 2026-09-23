# Raast receive QR (live accountDetails)

Corporate Raast page loads the merchant wallet QR from DFS:

`POST {DFS_APP_API_BASE_URL}/v1/corporate/accountDetails`  
Payload: `{ "channel": "MOB", "payload": { "mobileNumber": "<party phone>" } }`  
Auth: `X-Portal-Key`

Portal: `GET /api/transfers/live/raast/account-details` (JWT)

Uses `data.qrCode`, `iban`, `accountTitle`, `mobileNo`, `currentBalance`, `accountStatusDescr`.

Outbound Raast pay is not exposed in the portal until DFS provides a pay API.
