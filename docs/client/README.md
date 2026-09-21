# DFS Corporate Portal — Client Documents

Bank / EMI client pack for **DFS Corporate Portal**.

| Document | ID | Markdown | HTML | PDF |
|----------|----|----------|------|-----|
| Business Requirements Document | DFS-CP-BRD-001 | `BRD-DFS-Corporate-Portal.md` | `BRD-DFS-Corporate-Portal.html` | `pdf/BRD-DFS-Corporate-Portal.pdf` |
| Functional Specification Document | DFS-CP-FSD-001 | `FSD-DFS-Corporate-Portal.md` | `FSD-DFS-Corporate-Portal.html` | `pdf/FSD-DFS-Corporate-Portal.pdf` |
| Product Document | DFS-CP-PD-001 | `Product-Document-DFS-Corporate-Portal.md` | `Product-Document-DFS-Corporate-Portal.html` | `pdf/Product-Document-DFS-Corporate-Portal.pdf` |

## Regenerate PDFs

From this folder (PowerShell):

```powershell
.\export-pdfs.ps1
```

Requires Google Chrome. Opens each HTML file headless and writes A4 PDFs under `pdf/`.

## Manual PDF (any browser)

1. Open the `.html` file in Chrome or Edge  
2. Print → **Save as PDF**  
3. Enable **Background graphics** for cover colours and table headers  

## Notes

- Compliance stubs (NADRA BV, automated sanctions) and commercials are marked **TBD**  
- Styling: `assets/client-docs.css`  
