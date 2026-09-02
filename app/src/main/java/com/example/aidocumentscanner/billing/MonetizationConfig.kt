package com.example.aidocumentscanner.billing

/**
 * Phase-12 business rules.
 *
 * Keep false until the monetization activation gate is approved.
 * While false, existing advanced PDF tools remain free and Billing is a no-op.
 */
object MonetizationConfig {
    const val ENABLED: Boolean = false

    const val PRO_PRODUCT_ID =
        "docuscan_pro_lifetime"

    private val premiumPdfToolNames =
        setOf(
            "MERGE",
            "SPLIT",
            "REMOVE",
            "REORDER",
            "ROTATE",
            "WATERMARK",
            "PASSWORD"
        )

    fun isPremiumPdfTool(
        enumName: String
    ): Boolean =
        ENABLED &&
            enumName in premiumPdfToolNames

    fun premiumPdfToolNamesForTest():
        Set<String> =
        premiumPdfToolNames.toSet()
}
