# DocuScan — Monetization Activation Gate

Prepared: 2026-09-02

## Purpose
DocuScan follows the principle: **Monetization after product quality**.
The codebase defaults to `MonetizationConfig.ENABLED = false`.
This gate defines the mandatory preconditions before changing `ENABLED = true`.

## Activation Preconditions

1. **Phase 10 & 11 Quality Gated**
   - [x] All Phase 10 local unit tests and schema migrations passing.
   - [x] StrictMode penalty logging clean.
   - [x] App bundle targeting API 36 with 0 forbidden permissions.

2. **Google Play Console Product Active**
   - [ ] In-app product `docuscan_pro_lifetime` created and set to Active.
   - [ ] Localized pricing configured for major markets (INR, USD, GBP, EUR).
   - [ ] License testing configured for internal QA accounts.

3. **Internal Testing Track Validation**
   - [ ] Tested on Android 16 (API 36), Android 14 (API 34), and Android 8 (API 26).
   - [ ] Successful purchase tested with Google Play Billing 9.1.0.
   - [ ] Pending purchase tested with slow test instrument.
   - [ ] Declined purchase tested.
   - [ ] Restore purchase verified on second device.
   - [ ] Offline caching verified after purchase.

4. **Policy & Store Listing Alignment**
   - [x] Privacy policy explicitly discloses Google Play payment processing and local token handling.
   - [x] Data Safety questionnaire submitted matching client-only billing architecture.
   - [x] Store listing describes which features are free and which are unlocked by Lifetime Pro.

5. **Activation Procedure**
   - Update `MonetizationConfig.kt`: set `const val ENABLED: Boolean = true`.
   - Run `.\scripts\billing-release-check.ps1`.
   - Build signed production AAB.
   - Deploy as staged rollout starting at 10% on Google Play Production track.
