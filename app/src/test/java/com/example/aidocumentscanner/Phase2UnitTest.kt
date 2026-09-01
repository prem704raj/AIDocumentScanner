package com.example.aidocumentscanner

import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.util.BitmapLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2UnitTest {

    @Test
    fun bitmapLoader_defaultMaxDimensionIs2400() {
        assertEquals(2400, BitmapLoader.DEFAULT_MAX_DIMENSION)
    }

    @Test
    fun imageEnhancer_allFilterTypesDefined() {
        val actualFilters = ImageEnhancer.FilterType.entries.map { it.name }
        val expectedFilters = listOf(
            "ORIGINAL", "DOCUMENT", "BLACK_WHITE", "GRAYSCALE",
            "COLOR_ENHANCE", "MAGIC_COLOR", "LIGHTEN", "DARKEN", "SEPIA",
            "HIGH_CONTRAST", "SHARPEN", "INVERT", "WARM", "COOL"
        )
        for (filter in expectedFilters) {
            assertTrue(actualFilters.contains(filter))
        }
    }
}
