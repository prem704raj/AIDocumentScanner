package com.example.aidocumentscanner

import com.example.aidocumentscanner.pdf.PdfGenerator
import com.example.aidocumentscanner.privacy.ExportRegistry
import com.example.aidocumentscanner.privacy.PrivacyDataManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase8UnitTest {

    @Test
    fun privacySnapshot_totalAppManagedBytesCalculation() {
        val snapshot = PrivacyDataManager.Snapshot(
            documentCount = 10,
            indexedOcrDocuments = 8,
            studyDocumentCount = 4,
            subjectCount = 2,
            internalPdfBytes = 5_000_000L,
            thumbnailBytes = 200_000L,
            toolOutputBytes = 300_000L,
            crashLogBytes = 10_000L,
            temporaryCacheBytes = 50_000L,
            databaseBytes = 40_000L,
            crashReportCount = 1,
            trackedPublicCopies = 2,
            cameraPermissionGranted = true
        )

        val expected = 5_000_000L + 200_000L + 300_000L + 10_000L + 50_000L + 40_000L
        assertEquals(expected, snapshot.totalAppManagedBytes)
        assertEquals(10, snapshot.documentCount)
        assertEquals(8, snapshot.indexedOcrDocuments)
        assertEquals(4, snapshot.studyDocumentCount)
        assertEquals(2, snapshot.subjectCount)
        assertTrue(snapshot.cameraPermissionGranted)
    }

    @Test
    fun eraseResult_successConditions() {
        val successResult = PrivacyDataManager.EraseResult(
            publicCopiesDeletedOrMissing = 3,
            publicCopiesFailed = 0,
            errors = emptyList()
        )
        assertTrue(successResult.success)

        val failedPublicResult = PrivacyDataManager.EraseResult(
            publicCopiesDeletedOrMissing = 1,
            publicCopiesFailed = 1,
            errors = emptyList()
        )
        assertFalse(failedPublicResult.success)

        val failedLocalResult = PrivacyDataManager.EraseResult(
            publicCopiesDeletedOrMissing = 2,
            publicCopiesFailed = 0,
            errors = listOf("Could not clear local database")
        )
        assertFalse(failedLocalResult.success)
    }

    @Test
    fun exportRegistry_deleteResult() {
        val deleteResult = ExportRegistry.DeleteResult(
            deletedOrMissing = 5,
            failed = 0
        )
        assertEquals(5, deleteResult.deletedOrMissing)
        assertEquals(0, deleteResult.failed)
    }

    @Test
    fun formatFileSize_variousRanges() {
        assertEquals("500 B", PdfGenerator.formatFileSize(500L))
        assertEquals("1 KB", PdfGenerator.formatFileSize(1024L))
        assertEquals("10 KB", PdfGenerator.formatFileSize(10240L))
        assertEquals("1.0 MB", PdfGenerator.formatFileSize(1024L * 1024L))
        assertEquals("2.5 MB", PdfGenerator.formatFileSize((2.5 * 1024 * 1024).toLong()))
    }
}
