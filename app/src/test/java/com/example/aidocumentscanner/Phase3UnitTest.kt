package com.example.aidocumentscanner

import com.example.aidocumentscanner.scanner.DocumentScanner
import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.scanner.PerspectiveCorrector
import com.example.aidocumentscanner.util.BitmapLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3UnitTest {

    @Test
    fun testBitmapLoaderMaxDimension() {
        assertEquals(2400, BitmapLoader.DEFAULT_MAX_DIMENSION)
    }

    @Test
    fun testImageEnhancerFilterTypes() {
        val filters = ImageEnhancer.FilterType.entries.map { it.name }
        assertTrue(filters.contains("ORIGINAL"))
        assertTrue(filters.contains("DOCUMENT"))
        assertTrue(filters.contains("BLACK_WHITE"))
        assertTrue(filters.contains("GRAYSCALE"))
        assertTrue(filters.contains("COLOR_ENHANCE"))
        // Legacy compatibility
        assertTrue(filters.contains("MAGIC_COLOR"))
        assertTrue(filters.contains("SEPIA"))
    }

    @Test
    fun testDocumentScannerStructures() {
        val scanResult = DocumentScanner.ScanResult(
            corners = emptyList(),
            confidence = 0.8f
        )
        assertEquals(0, scanResult.corners.size)
        assertEquals(0.8f, scanResult.confidence, 0.001f)
    }
}
