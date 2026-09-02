# DocuScan — Billing QA & Verification Scenarios

Prepared: 2026-09-02

## Core Scenarios

### 1. Monetization Disabled Baseline (`ENABLED = false`)
- Open PDF Tools: All tools display normal cards with NO "PRO • LOCKED" badges.
- Tapping Merge, Split, Rotate, etc. opens the tool directly without any paywall.
- Settings screen does NOT display the DocuScan Pro row.
- Direct navigation to Pro screen displays disabled status indicator.

### 2. Monetization Enabled Baseline (`ENABLED = true`, Free User)
- Free tools (PDF to images, Images to PDF, Optimize, Rename) work without any gate.
- Core scanner, OCR search, Study Mode, and Privacy dashboard remain 100% free and open.
- Premium tools (Merge, Split, Remove pages, Reorder pages, Rotate pages, Watermark, Password protect) display `PRO • LOCKED` badge.
- Tapping a premium tool opens `ProScreen` with localized price and feature breakdown.
- Settings displays quiet "Unlock DocuScan Pro" row.

### 3. Purchase Flow (Instant Success)
- In `ProScreen`, tap "Unlock Lifetime Pro".
- Google Play purchase bottom sheet appears with correct product name and price.
- Complete purchase using test payment instrument.
- App acknowledges purchase within 3 seconds.
- UI transitions to "Lifetime Pro is active" and "Pro unlocked".
- In PDF Tools, badges update from `PRO • LOCKED` to `PRO`. All tools now open freely.

### 4. Pending Payment Flow (Delayed Instrument)
- Complete purchase using "Slow test card".
- Purchase state enters `PENDING`.
- Pro is NOT granted.
- `ProScreen` displays: "Payment is pending. Free features remain available and Pro unlocks only after Google Play confirms payment."
- Core app continues working in free mode.
- When Play confirms payment in the background, next query or app launch unlocks Pro and acknowledges.

### 5. Purchase Cancellation / Decline
- Open purchase sheet and dismiss or select declined test card.
- App returns cleanly without crashing.
- `purchasing` state resets to false; no entitlement granted.

### 6. Restore Purchases & Already Owned
- On fresh install or another device with same Play account, user taps "Restore purchase".
- `queryPurchasesAsync` detects `docuscan_pro_lifetime` in `PURCHASED` state.
- `EntitlementStore` updates local DataStore to `isPro = true`.
- App displays "DocuScan Pro restored."

### 7. Offline Resilience
- User purchases Pro while connected.
- Device enters Airplane mode (no connectivity).
- App restarts: Local `EntitlementStore` preserves cached `isPro = true`.
- Premium tools remain accessible.
- Billing client connection failure does NOT revoke cached lifetime status.

### 8. Refund / Revocation Reconciliation
- User receives Play refund for Pro purchase.
- On next successful online Play check, `queryPurchasesAsync` returns empty list.
- `EntitlementStore` reconciles and sets `isPro = false`.
- Premium tools return to `PRO • LOCKED` state.
