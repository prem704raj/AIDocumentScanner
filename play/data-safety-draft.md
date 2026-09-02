# DocuScan — Google Play Data Safety Draft After Phase 12

Prepared: 2026-09-02
STATUS: DRAFT UNTIL FINAL SIGNED-BUNDLE AUDIT

Assumptions:
- no android.permission.INTERNET in DocuScan's app manifest
- Google Play Billing Library 9.1.0
- one non-consumable one-time product
- no ads SDK
- no analytics SDK
- no remote crash SDK
- no DocuScan login/auth SDK
- no cloud storage SDK
- no developer-operated backend
- bundled/local OCR
- user-triggered Android sharing only
- client-only entitlement handling

## Preliminary direction

Does the app collect/share required user data with the developer?
PRELIMINARY: NO, if the final build remains exactly client-only as designed.

Reason:
- PDFs/OCR/subjects remain local
- Google Play handles payment credentials directly
- DocuScan never accesses the user's card number/payment credentials
- purchase state is used on-device for entitlement
- purchase token is not sent to a DocuScan backend
- user-triggered Share occurs only after explicit user action

Google Play's Data Safety guidance says payment information collected directly by
Google Play Billing for the payment transaction generally does not need to be
declared by the app as its own payment-info collection when the app never accesses it.

DocuScan DOES receive purchase/product/token information from Play Billing.
In this implementation it remains on-device and is not transmitted to the developer.

## Product

`docuscan_pro_lifetime`

Non-consumable one-time product.
No subscription.

## Local entitlement data

Persisted:
- last-known Pro-owned boolean
- last successful Play ownership-check timestamp

Not intentionally persisted:
- payment card details
- payment credentials
- raw purchase token

## Deletion

"Erase document data" deletes user document/OCR/study content but does NOT erase
a paid Google Play entitlement. Purchase rights are restored/rechecked using the
Play account after reinstall.

## This draft becomes invalid if later adding

- ads
- analytics
- backend verification
- cloud sync
- remote crash reporting
- account/login
- remote OCR/AI
- telemetry
- Play Integrity or anti-fraud integrations with additional data handling
- third-party SDKs transmitting identifiers/diagnostics

## Final audit

1. Inspect signed release merged manifest.
2. Confirm expected Billing permission/metadata.
3. Confirm no unexpected INTERNET permission.
4. Run dependency tree.
5. Search for network/analytics/ad/auth/cloud code.
6. Confirm purchase token is not uploaded anywhere.
7. Reconcile with docs/privacy-policy.html.
8. Complete Play Data Safety from the actual signed artifact.

The developer remains responsible for the final declaration.
