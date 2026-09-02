package com.example.aidocumentscanner

import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.ui.screens.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6UnitTest {

    @Test
    fun storageLocation_enumProperties() {
        assertEquals("App storage", StorageLocation.INTERNAL.label)
        assertEquals("Documents folder", StorageLocation.DOCUMENTS.label)
        assertEquals("Downloads folder", StorageLocation.DOWNLOADS.label)

        assertTrue(StorageLocation.INTERNAL.description.contains("Private local storage"))
    }

    @Test
    fun documentSorting_recentNamePages() {
        val doc1 = Document(
            id = 1L,
            name = "Charlie.pdf",
            pdfPath = "/path/1",
            thumbnailPath = null,
            pageCount = 10,
            size = 1024L,
            createdAt = 1000L,
            updatedAt = 3000L
        )
        val doc2 = Document(
            id = 2L,
            name = "Alpha.pdf",
            pdfPath = "/path/2",
            thumbnailPath = null,
            pageCount = 2,
            size = 2048L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val doc3 = Document(
            id = 3L,
            name = "bravo.pdf",
            pdfPath = "/path/3",
            thumbnailPath = null,
            pageCount = 25,
            size = 4096L,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val list = listOf(doc1, doc2, doc3)

        // Recent: doc1 (3000), doc3 (2000), doc2 (1000)
        val byRecent = list.sortedByDescending { it.updatedAt }
        assertEquals(listOf(1L, 3L, 2L), byRecent.map { it.id })

        // Name: Alpha, bravo, Charlie
        val byName = list.sortedBy { it.name.lowercase() }
        assertEquals(listOf(2L, 3L, 1L), byName.map { it.id })

        // Pages: doc3 (25), doc1 (10), doc2 (2)
        val byPages = list.sortedByDescending { it.pageCount }
        assertEquals(listOf(3L, 1L, 2L), byPages.map { it.id })
    }

    @Test
    fun documentFiltering_caseInsensitive() {
        val doc1 = Document(
            id = 1L,
            name = "Tax Return 2026.pdf",
            pdfPath = "/path/1",
            thumbnailPath = null,
            pageCount = 4,
            size = 1024L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val doc2 = Document(
            id = 2L,
            name = "Electricity Bill.pdf",
            pdfPath = "/path/2",
            thumbnailPath = null,
            pageCount = 1,
            size = 512L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val list = listOf(doc1, doc2)
        val filtered = list.filter { it.name.contains("tax", ignoreCase = true) }

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)
    }
}
