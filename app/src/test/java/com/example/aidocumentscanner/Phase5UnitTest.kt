package com.example.aidocumentscanner

import com.example.aidocumentscanner.ocr.OcrEngine
import com.example.aidocumentscanner.ocr.OcrTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5UnitTest {

    @Test
    fun ocrTextCodec_roundTrip() {
        val pages = listOf(
            OcrTextCodec.PageText(0, "Invoice #12345\nDate: 2026-09-01\nTotal: $500.00"),
            OcrTextCodec.PageText(1, "Terms and Conditions:\nAll payments are final.\nThank you.")
        )
        val encoded = OcrTextCodec.encode(pages)
        assertTrue(encoded.startsWith("DOCUSCAN_OCR_V1\n"))

        val decoded = OcrTextCodec.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals(0, decoded[0].pageIndex)
        assertEquals(pages[0].text, decoded[0].text)
        assertEquals(1, decoded[1].pageIndex)
        assertEquals(pages[1].text, decoded[1].text)
    }

    @Test
    fun ocrTextCodec_backwardCompatibilityLegacyText() {
        val legacy = "This is some plain legacy text from older version"
        val decoded = OcrTextCodec.decode(legacy)
        assertEquals(1, decoded.size)
        assertEquals(0, decoded[0].pageIndex)
        assertEquals(legacy, decoded[0].text)
    }

    @Test
    fun ocrTextCodec_nullAndEmpty() {
        assertTrue(OcrTextCodec.decode(null).isEmpty())
        assertTrue(OcrTextCodec.decode("").isEmpty())
    }

    @Test
    fun ocrTextCodec_wordCountAndCombined() {
        val pages = listOf(
            OcrTextCodec.PageText(0, "Hello World from DocuScan"),
            OcrTextCodec.PageText(1, "Second page text here")
        )
        assertEquals(8, OcrTextCodec.wordCount(pages))

        val combined = OcrTextCodec.combinedHumanReadable(pages)
        assertTrue(combined.contains("Page 1\nHello World from DocuScan"))
        assertTrue(combined.contains("Page 2\nSecond page text here"))
    }

    @Test
    fun ocrEngine_searchKeywordMatches() {
        val pages = listOf(
            OcrEngine.PageOcrResult(
                pageIndex = 0,
                result = OcrEngine.OcrResult("Agreement with Acme Corp on September 2026", emptyList())
            ),
            OcrEngine.PageOcrResult(
                pageIndex = 1,
                result = OcrEngine.OcrResult("Confidential information concerning Acme Corp operations", emptyList())
            )
        )

        val matches = OcrEngine.searchKeyword(pages, "acme", caseSensitive = false)
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].pageIndex)
        assertTrue(matches[0].context.contains("Acme Corp"))
        assertEquals(1, matches[1].pageIndex)
        assertTrue(matches[1].context.contains("Acme Corp"))
    }

    @Test
    fun ocrEngine_searchKeywordNotFound() {
        val pages = listOf(
            OcrEngine.PageOcrResult(
                pageIndex = 0,
                result = OcrEngine.OcrResult("Standard document text", emptyList())
            )
        )

        val matches = OcrEngine.searchKeyword(pages, "NonexistentKeyword", caseSensitive = false)
        assertTrue(matches.isEmpty())
    }
}
