package com.example.aidocumentscanner

import com.example.aidocumentscanner.pdf.PageSpecParser
import com.example.aidocumentscanner.pdf.PdfEditor
import com.example.aidocumentscanner.pdf.PdfToolFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4UnitTest {

    @Test
    fun pageSpecParser_selectionValid() {
        val result = PageSpecParser.parseSelection("1,3,5-8", 10).getOrThrow()
        assertEquals(listOf(1, 3, 5, 6, 7, 8), result)
    }

    @Test
    fun pageSpecParser_selectionRejectsOutOfRange() {
        val result = PageSpecParser.parseSelection("1,11", 10)
        assertTrue(result.isFailure)
    }

    @Test
    fun pageSpecParser_selectionRejectsReverseRange() {
        val result = PageSpecParser.parseSelection("8-5", 10)
        assertTrue(result.isFailure)
    }

    @Test
    fun pageSpecParser_splitGroupsValid() {
        val groups = PageSpecParser.parseSplitGroups("1-3;4-6;7-10", 10).getOrThrow()
        assertEquals(3, groups.size)
        assertEquals(listOf(1, 2, 3), groups[0])
        assertEquals(listOf(4, 5, 6), groups[1])
        assertEquals(listOf(7, 8, 9, 10), groups[2])
    }

    @Test
    fun pageSpecParser_splitGroupsRejectsOverlap() {
        val result = PageSpecParser.parseSplitGroups("1-4;4-7", 10)
        assertTrue(result.isFailure)
    }

    @Test
    fun pageSpecParser_orderValid() {
        val order = PageSpecParser.parseOrder("3,1,4,2", 4).getOrThrow()
        assertEquals(listOf(3, 1, 4, 2), order)
    }

    @Test
    fun pageSpecParser_orderRejectsIncomplete() {
        val result = PageSpecParser.parseOrder("3,1,2", 4)
        assertTrue(result.isFailure)
    }

    @Test
    fun pageSpecParser_allPagesSpec() {
        assertEquals("1,2,3,4,5", PageSpecParser.allPagesSpec(5))
        assertEquals("", PageSpecParser.allPagesSpec(0))
    }

    @Test
    fun pdfToolFileManager_sanitizePdfName() {
        assertEquals("Invoice.pdf", PdfToolFileManager.sanitizePdfName("Invoice"))
        assertEquals("My Document.pdf", PdfToolFileManager.sanitizePdfName("path/to/My Document.pdf"))
        assertEquals("Document.pdf", PdfToolFileManager.sanitizePdfName("   "))
    }

    @Test
    fun pdfEditor_optimizationResultMath() {
        val result = PdfEditor.OptimizationResult(
            outputPath = "/tmp/opt.pdf",
            originalBytes = 1_000_000L,
            optimizedBytes = 800_000L
        )
        assertEquals(200_000L, result.savedBytes)
        assertEquals(20.0, result.reductionPercent, 0.001)
    }
}
