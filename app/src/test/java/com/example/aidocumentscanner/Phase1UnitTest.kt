package com.example.aidocumentscanner

import com.example.aidocumentscanner.data.AppDatabase
import com.example.aidocumentscanner.navigation.Screen
import com.example.aidocumentscanner.ocr.OcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1UnitTest {

    @Test
    fun ocrEngine_searchKeyword_preservesPageIndexAndFindsMatches() {
        val page0 = OcrEngine.PageOcrResult(
            pageIndex = 0,
            result = OcrEngine.OcrResult(
                fullText = "Introduction to the project documentation.",
                blocks = emptyList()
            )
        )
        val page1 = OcrEngine.PageOcrResult(
            pageIndex = 1,
            result = OcrEngine.OcrResult(
                fullText = "Here are the confidential meeting notes and terms.",
                blocks = emptyList()
            )
        )
        val page2 = OcrEngine.PageOcrResult(
            pageIndex = 2,
            result = OcrEngine.OcrResult(
                fullText = "Final section with CONFIDENTIAL stamp at the end.",
                blocks = emptyList()
            )
        )

        val results = OcrEngine.searchKeyword(listOf(page0, page1, page2), "confidential", caseSensitive = false)

        assertEquals(2, results.size)
        assertEquals(1, results[0].pageIndex)
        assertEquals("confidential", results[0].matchedText.lowercase())
        assertEquals(2, results[1].pageIndex)
        assertEquals("CONFIDENTIAL", results[1].matchedText)
    }

    @Test
    fun ocrEngine_combinedText_separatorRoundtrip() {
        val page0 = OcrEngine.PageOcrResult(
            pageIndex = 0,
            result = OcrEngine.OcrResult(fullText = "Page 1 Content", blocks = emptyList())
        )
        val page1 = OcrEngine.PageOcrResult(
            pageIndex = 1,
            result = OcrEngine.OcrResult(fullText = "Page 2 Content", blocks = emptyList())
        )

        val combined = OcrEngine.getCombinedText(listOf(page0, page1))
        val split = OcrEngine.splitCombinedText(combined)

        assertEquals(2, split.size)
        assertEquals("Page 1 Content", split[0])
        assertEquals("Page 2 Content", split[1])
    }

    @Test
    fun ocrEngine_countWords_calculatesAccurateCount() {
        val page0 = OcrEngine.PageOcrResult(
            pageIndex = 0,
            result = OcrEngine.OcrResult(fullText = "Hello world from DocuScan", blocks = emptyList())
        )
        val page1 = OcrEngine.PageOcrResult(
            pageIndex = 1,
            result = OcrEngine.OcrResult(fullText = "Fast and private offline scanning", blocks = emptyList())
        )

        val words = OcrEngine.countWords(listOf(page0, page1))
        assertEquals(9, words)
    }

    @Test
    fun navigation_pdfViewerRoute_encodesPageParameter() {
        val routeWithPage0 = Screen.PdfViewer.createRoute(42L, 0)
        assertEquals("pdf_viewer/42?page=0", routeWithPage0)

        val routeWithPage5 = Screen.PdfViewer.createRoute(100L, 5)
        assertEquals("pdf_viewer/100?page=5", routeWithPage5)

        val routeWithNegativePage = Screen.PdfViewer.createRoute(100L, -1)
        assertEquals("pdf_viewer/100?page=0", routeWithNegativePage)
    }

    @Test
    fun appDatabase_migrationsDefined() {
        assertNotNull(AppDatabase.MIGRATION_1_2)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)

        assertNotNull(AppDatabase.MIGRATION_2_3)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
    }
}
