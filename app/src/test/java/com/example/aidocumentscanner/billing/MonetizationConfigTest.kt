package com.example.aidocumentscanner.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationConfigTest {

    @Test
    fun defaultMonetizationIsDisabledForSafety() {
        assertFalse(
            "MonetizationConfig.ENABLED must default to false until explicit launch activation",
            MonetizationConfig.ENABLED
        )
    }

    @Test
    fun proProductIdIsImmutableConstant() {
        assertEquals("docuscan_pro_lifetime", MonetizationConfig.PRO_PRODUCT_ID)
    }

    @Test
    fun premiumToolsSetMatchesDirective() {
        val expected = setOf(
            "MERGE",
            "SPLIT",
            "REMOVE",
            "REORDER",
            "ROTATE",
            "WATERMARK",
            "PASSWORD"
        )
        assertEquals(expected, MonetizationConfig.premiumPdfToolNamesForTest())
    }

    @Test
    fun isPremiumPdfToolReturnsFalseWhenDisabled() {
        // While ENABLED == false, no tool is considered premium/locked
        val tools = listOf("MERGE", "SPLIT", "REMOVE", "REORDER", "ROTATE", "WATERMARK", "PASSWORD")
        for (tool in tools) {
            assertFalse(
                "Tool $tool must not be premium while monetization is disabled",
                MonetizationConfig.isPremiumPdfTool(tool)
            )
        }
    }

    @Test
    fun freeToolsAreNeverInPremiumSet() {
        val freeTools = listOf("EXTRACT_IMAGES", "IMAGES_TO_PDF", "OPTIMIZE", "RENAME")
        val premiumSet = MonetizationConfig.premiumPdfToolNamesForTest()
        for (tool in freeTools) {
            assertFalse(
                "Tool $tool must never be in premium set",
                tool in premiumSet
            )
        }
    }
}
