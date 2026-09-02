# DocuScan — Play Console Launch Checklist

Prepared: 2026-09-02

## Phase 10 gate
[x] PHASE_10_RELEASE_STATUS.md = GO
[x] no unresolved P0
[x] minified release smoke passed
[x] API 26-36 matrix completed
[x] privacy/merged-manifest audit passed

## Permanent applicationId
Historical source:
`com.example.aidocumentscanner`

If never published:
recommended permanent ID:
`com.premraj.docuscan`

If an existing Play app already uses the historical package:
preserve that Play identity.

[ ] RESOLVED
Evidence: __________________________

## iText
Resolve one:
[ ] commercial license
[ ] intentional AGPL-compliant distribution
[ ] verified PDF-engine replacement

[ ] RESOLVED
Evidence: __________________________

## Signing
[ ] upload key generated locally
[ ] `.jks` not committed
[ ] `keystore.properties` not committed
[ ] upload key/password backed up securely
[ ] signed release AAB verified
[ ] Play App Signing configured

## Store listing
[x] App name: DocuScan: PDF Scanner
[x] Category: Productivity
[x] short description <= 80
[x] full description <= 4000
[x] icon 512x512
[x] feature graphic 1024x500
[x] 6 real sanitized phone screenshots
[x] no rankings/price/testimonials/misleading AI claims

## Privacy policy
Expected:
`https://prem704raj.github.io/AIDocumentScanner/privacy-policy.html`

[ ] GitHub Pages enabled from /docs
[ ] URL returns HTTP 200 publicly
[x] same policy accessible in-app
[x] policy matches final build

## App content
Ads:
[x] No

App access:
[x] No login/reviewer credentials needed

Target audience:
[x] 13-15
[x] 16-17
[x] 18+
[x] no under-13 groups for this release

Content rating:
[ ] IARC complete
[ ] app not Unrated

Data Safety:
[x] completed from final bundle audit
[x] matches privacy policy

Other:
[ ] all current Play "Needs attention" items handled

## Target API
[x] targetSdk >= 36
[x] compile/release tested on API 36

## AAB/versioning
[x] signed `.aab` uploaded
[x] versionCode has never been used for this Play app
[x] release notes supplied

## Testing
Internal:
[ ] completed

Closed:
[ ] completed with useful feedback

If personal account created after November 13, 2023:
[ ] >=12 testers opted in
[ ] continuously opted in >=14 days
[ ] production-access application completed

If account is not subject to that rule:
[ ] do not claim the rule is mandatory

## Developer/package verification
Updated package-registration requirements become effective September 30, 2026.

[ ] developer identity verified
[ ] final package registered/recognized
[ ] developer profile/contact accurate

## Final review
[ ] countries selected
[ ] initial app free
[ ] Play warnings reviewed
[ ] pre-launch report reviewed
[ ] accessibility report reviewed
[ ] policy status clean
[ ] Managed Publishing decision made
[ ] send for review

## Post-launch
Monitor first 72 hours:
[ ] crashes
[ ] ANRs
[ ] camera failures
[ ] PDF corruption
[ ] install issues
[ ] review complaints

Phase 12 owns monetization.
