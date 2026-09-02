$ErrorActionPreference = "Stop"

Write-Host "DocuScan Phase 10 Release Verification" -ForegroundColor Cyan

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail($message) {
    Write-Host "FAILED: $message" -ForegroundColor Red
    exit 1
}

$manifest = "app/src/main/AndroidManifest.xml"
$gradleFile = "app/build.gradle.kts"

if (-not (Test-Path $manifest)) {
    Fail "AndroidManifest.xml not found"
}

if (-not (Test-Path $gradleFile)) {
    Fail "app/build.gradle.kts not found"
}

$manifestText = Get-Content $manifest -Raw
$gradleText = Get-Content $gradleFile -Raw

$forbiddenPermissions = @(
    "android.permission.INTERNET",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO"
)

foreach ($permission in $forbiddenPermissions) {
    if ($manifestText.Contains($permission)) {
        Fail "Forbidden permission found: $permission"
    }
}

if (-not $manifestText.Contains("android.permission.CAMERA")) {
    Fail "CAMERA permission missing"
}

if (-not $manifestText.Contains('android:allowBackup="false"')) {
    Fail "allowBackup must stay false"
}

if ($gradleText -notmatch 'targetSdk\s*=\s*36') {
    Fail "targetSdk must be 36"
}

if ($gradleText -notmatch 'compileSdk\s*=\s*36') {
    Fail "compileSdk must be 36"
}

Write-Host "Static privacy/build checks passed." -ForegroundColor Green

$tasks = @(
    ":app:testDebugUnitTest",
    ":app:lintDebug",
    ":app:lintRelease",
    ":app:assembleDebug",
    ":app:assembleRelease",
    ":app:bundleRelease"
)

& .\gradlew.bat @tasks --stacktrace

if ($LASTEXITCODE -ne 0) {
    Fail "Gradle verification failed"
}

$bundle = Get-ChildItem `
    "app/build/outputs/bundle/release/*.aab" `
    -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($null -eq $bundle) {
    Fail "Release AAB was not produced"
}

$hash = Get-FileHash $bundle.FullName -Algorithm SHA256

Write-Host ""
Write-Host "Release bundle:" $bundle.FullName -ForegroundColor Green
Write-Host "SHA-256:" $hash.Hash
Write-Host ""
Write-Host "AUTOMATED CHECKS PASSED." -ForegroundColor Green
Write-Host "Do not ship yet: complete the Phase-10 device/manual matrix and resolve iText/applicationId release gates."
