# Export DFS Corporate Portal client docs to PDF (Chrome headless)
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$PdfDir = Join-Path $Root "pdf"
New-Item -ItemType Directory -Force -Path $PdfDir | Out-Null

$Chrome = @(
  "${env:ProgramFiles}\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe",
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $Chrome) { throw "Chrome or Edge not found." }

$Docs = @(
  "BRD-DFS-Corporate-Portal.html",
  "FSD-DFS-Corporate-Portal.html",
  "Product-Document-DFS-Corporate-Portal.html"
)

function To-FileUri([string]$Path) {
  $full = (Resolve-Path $Path).Path -replace '\\', '/'
  if ($full -match '^[A-Za-z]:') { return "file:///$full" }
  return "file://$full"
}

foreach ($doc in $Docs) {
  $html = Join-Path $Root $doc
  $pdfName = [IO.Path]::GetFileNameWithoutExtension($doc) + ".pdf"
  $pdf = Join-Path $PdfDir $pdfName
  if (Test-Path $pdf) { Remove-Item $pdf -Force }
  $uri = To-FileUri $html
  Write-Host "Exporting $doc -> pdf\$pdfName"
  $proc = Start-Process -FilePath $Chrome -ArgumentList @(
    "--headless=new",
    "--disable-gpu",
    "--no-pdf-header-footer",
    "--print-to-pdf=$pdf",
    $uri
  ) -PassThru -WindowStyle Hidden
  $deadline = (Get-Date).AddSeconds(60)
  while (-not (Test-Path $pdf) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 500
  }
  if (-not $proc.HasExited) {
    try { $proc.WaitForExit(15000) } catch { }
  }
  # Chrome may finish writing shortly after process exit
  $deadline = (Get-Date).AddSeconds(15)
  while ((-not (Test-Path $pdf) -or (Get-Item $pdf).Length -lt 1000) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 500
  }
  if (-not (Test-Path $pdf) -or (Get-Item $pdf).Length -lt 1000) {
    throw "Failed to create $pdf"
  }
  $size = (Get-Item $pdf).Length
  Write-Host "  OK ($size bytes)"
}

Write-Host "Done. PDFs in $PdfDir"
