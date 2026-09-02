# DocuScan Lifetime Pro — Google Play Product Setup

Prepared: 2026-09-02

## Product
Type: non-consumable one-time in-app product
Product ID: `docuscan_pro_lifetime`
Name: DocuScan Pro Lifetime
Description: Unlock advanced PDF editing tools with one purchase.
Purchase option: Buy
Recommended purchase option ID: `buy-lifetime`

Do NOT configure:
- subscription
- rent
- preorder
- consumable
- multi-quantity

## Pricing Strategy
Recommended launch pricing:
- India (INR): ₹199
- United States (USD): $3.99
- United Kingdom (GBP): £3.49
- Eurozone (EUR): €3.99
- Other countries: Configure individually in Play Console based on local purchasing power parity.

Runtime Price Display:
The app dynamically displays Google Play's localized formattedPrice (`ProductDetails.OneTimePurchaseOfferDetails.formattedPrice`). Prices are never hard-coded in the app.

## License Testing Setup
1. In Google Play Console, navigate to **Setup → License testing**.
2. Add tester Gmail accounts to the license testing list.
3. Configure **License test response** to `RESPOND_NORMALLY`.
4. Test scenarios available for license testers:
   - Successful purchase (instant approval)
   - Slow test card (approves after a few minutes to test `PENDING` state)
   - Declined test card (to test `USER_CANCELED` / declined flows)
