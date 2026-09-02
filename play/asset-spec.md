# DocuScan Play Store Asset Specification

Prepared: 2026-09-02

## Store icon
Required final upload:
- 512 x 512 px
- 32-bit PNG
- <= 1024 KB

Master:
`play/assets/docuscan-store-icon-master.svg`

Design:
- Blue #2563EB background
- White document
- Cyan #67E8F9 scanner brackets
- No text
- No AI sparkle
- No ranking/price/store badges
- Do not pre-round the source

Export:
`play/assets/docuscan-store-icon-512.png`

## Feature graphic
Required:
- 1024 x 500 px
- JPEG or 24-bit PNG
- no alpha

Master:
`play/assets/docuscan-feature-graphic-master.svg`

Export:
`play/assets/docuscan-feature-graphic-1024x500.png`

## Phone screenshots
Launch baseline:
6 portrait screenshots at 1080 x 1920.

Google Play requires at least 2 screenshots. Use 6 to show the complete real product.

Storyboard:
1. Home — "Scan, search and organize"
2. Editor — "Clean pages before saving"
3. Study Mode — "Organize scans by subject"
4. Search — "Search inside your PDFs"
5. PDF Tools — "Merge, split and manage PDFs"
6. Privacy — "See what stays local"

Rules:
- actual current release UI
- synthetic demo documents only
- no real IDs, marks, names, emails, phones, bank/medical data
- no notification/private status data
- no device frame or hand/finger
- no "#1", "best", "free", "sale"
- no "100% offline"
- no "AI scanner"
- first 3 screenshots prioritize actual UI
- do not advertise features that are not in the tested build

## Large screens
Only add tablet marketing assets after Phase-10 tablet/large-screen QA actually passes.
Do not use screenshots to imply optimization that is not real.

## Source control
Commit:
- SVG masters
- final Play PNG assets
- sanitized screenshot finals

Recommended:
play/assets/
play/screenshots/phone/
