# DocuScan — Google Play Data Safety Draft

Prepared: 2026-09-02
STATUS: DRAFT UNTIL FINAL RELEASE BUNDLE AUDIT

Assumptions:
- no INTERNET permission in merged release manifest
- no ads SDK
- no analytics SDK
- no remote crash SDK
- no login/auth SDK
- no cloud storage SDK
- no developer-operated backend
- bundled/local OCR
- user-triggered Android sharing only

## Preliminary direction

Does the app collect or share required user data with the developer?
PRELIMINARY: NO

Reason:
Google Play guidance excludes data that is only accessed/processed on-device and never
transmitted off-device from collected data.

DocuScan stores PDFs, OCR text, metadata and subjects locally.

User-triggered Share:
Google Play has a user-initiated sharing exception when the user reasonably expects the data
to be shared after a specific action.

## This draft becomes invalid if a later build adds
- advertising
- analytics
- cloud sync
- remote crash reporting
- account/login backend
- remote OCR/AI
- telemetry
- third-party SDKs that transmit identifiers/diagnostics

## Deletion
In-app:
Settings -> Privacy & data -> Erase document data

Account deletion:
Not applicable because current DocuScan creates no account.

## Final audit
1. Inspect RELEASE merged manifest.
2. Run `:app:dependencies`.
3. Search code/resources for network/analytics/ad/auth/cloud SDKs.
4. Review Play SDK warnings.
5. Reconcile with privacy policy.
6. Finalize Data Safety only after the final signed bundle is known.

The developer remains responsible for accurate Play declarations, including SDK behavior.
