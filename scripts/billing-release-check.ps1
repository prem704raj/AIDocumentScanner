$ErrorActionPreference = "Stop"

Write-Host "DocuScan Phase 12 Billing Release Verification" -ForegroundColor Cyan

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

Require-File "app/src/main/java/com/example/aidocumentscanner/billing/MonetizationConfig.kt"
Require-File "app/src/main/java/com/example/aidocumentscanner/billing/EntitlementStore.kt"
Require-File "app/src/main/java/com/example/aidocumentscanner/billing/PlayBillingManager.kt"
Require-File "app/src/main/java/com/example/aidocumentscanner/ui/screens/ProScreen.kt"
Require-File "play/docuscan-pro-product-setup.md"
Require-File "play/billing-qa.md"
Require-File "play/monetization-activation-gate.md"

$configText = Get-Content "app/src/main/java/com/example/aidocumentscanner/billing/MonetizationConfig.kt" -Raw
$tomlText = Get-Content "gradle/libs.versions.toml" -Raw

if ($tomlText -notmatch 'playBilling\s*=\s*"9\.1\.0"') {
    Fail "Play Billing version must be 9.1.0"
}

if ($configText -notmatch 'PRO_PRODUCT_ID\s*=\s*"docuscan_pro_lifetime"') {
    Fail "PRO_PRODUCT_ID must be docuscan_pro_lifetime"
}

Write-Host "Running Play release checks and JVM unit tests..." -ForegroundColor Yellow

& powershell `
    -ExecutionPolicy Bypass `
    -File ".\scripts\play-release-check.ps1"

if ($LASTEXITCODE -ne 0) {
    Fail "Play release check failed"
}

Write-Host ""
Write-Host "PHASE 12 BILLING CODE VERIFICATION PASSED" -ForegroundColor Green
Write-Host "Monetization activation gate: play/monetization-activation-gate.md"
Write-Host "Product setup: play/docuscan-pro-product-setup.md"
