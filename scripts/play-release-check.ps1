$ErrorActionPreference = "Stop"

Write-Host "DocuScan Phase 11 Play Release Gate" -ForegroundColor Cyan

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail($message) {
    Write-Host "FAILED: $message" -ForegroundColor Red
    exit 1
}

function Require-File($path) {
    if (-not (Test-Path $path)) {
        Fail "Missing required file: $path"
    }
}

Require-File "app/build.gradle.kts"
Require-File "app/src/main/AndroidManifest.xml"
Require-File "docs/privacy-policy.html"
Require-File "play/store-listing-en-US.txt"
Require-File "play/assets/docuscan-store-icon-master.svg"
Require-File "play/assets/docuscan-feature-graphic-master.svg"
Require-File "play/release-gates.md"

$gradleText = Get-Content "app/build.gradle.kts" -Raw
$manifestText = Get-Content "app/src/main/AndroidManifest.xml" -Raw
$listingText = Get-Content "play/store-listing-en-US.txt" -Raw

if ($gradleText -notmatch 'targetSdk\s*=\s*36') {
    Fail "targetSdk must be 36"
}

if ($manifestText.Contains("android.permission.INTERNET")) {
    Fail "Unexpected INTERNET permission in source manifest"
}

if ($manifestText -notmatch 'android:allowBackup="false"') {
    Fail "allowBackup must remain false"
}

$forbiddenClaims = @(
    "#1",
    "Best scanner",
    "100% Offline",
    "No trace left behind",
    "AI scanner",
    "<2 MB guaranteed"
)

foreach ($claim in $forbiddenClaims) {
    if ($listingText.Contains($claim)) {
        Fail "Forbidden or misleading listing claim: $claim"
    }
}

Write-Host "Running Phase-10 automated checks..." -ForegroundColor Yellow

& powershell `
    -ExecutionPolicy Bypass `
    -File ".\scripts\release-check.ps1"

if ($LASTEXITCODE -ne 0) {
    Fail "Phase-10 release checks failed"
}

Write-Host "Building release AAB..." -ForegroundColor Yellow
& .\gradlew.bat :app:bundleRelease --stacktrace

if ($LASTEXITCODE -ne 0) {
    Fail "bundleRelease failed"
}

$bundle = Get-ChildItem `
    "app/build/outputs/bundle/release/*.aab" `
    -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($null -eq $bundle) {
    Fail "Release AAB not found"
}

$hash = Get-FileHash `
    $bundle.FullName `
    -Algorithm SHA256

Write-Host ""
Write-Host "RELEASE AAB READY FOR PLAY CONSOLE VALIDATION" -ForegroundColor Green
Write-Host "Bundle:" $bundle.FullName
Write-Host "SHA-256:" $hash.Hash
Write-Host ""
Write-Host "Still requires human/legal Play gates:"
Write-Host "- applicationId"
Write-Host "- iText"
Write-Host "- privacy policy URL"
Write-Host "- Data Safety/content rating"
Write-Host "- account testing/production access"
Write-Host ""
Write-Host "Do not publish until play/play-console-checklist.md is complete."
