package com.example.aidocumentscanner

import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.domain.search.DocumentSearchResult
import com.example.aidocumentscanner.storage.DocumentFileStore
import com.example.aidocumentscanner.ui.documents.DocumentSort
import com.example.aidocumentscanner.ui.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase9UnitTest {

    @Test
    fun documentSearchResult_propertiesAndSource() {
        val result1 = DocumentSearchResult(
            documentId = 42L,
            documentName = "Assignment1.pdf",
            pageIndex = 0,
            context = "Document name: Assignment1.pdf",
            source = DocumentSearchResult.Source.DOCUMENT_NAME
        )
        assertEquals(42L, result1.documentId)
        assertEquals("Assignment1.pdf", result1.documentName)
        assertEquals(0, result1.pageIndex)
        assertEquals(DocumentSearchResult.Source.DOCUMENT_NAME, result1.source)

        val result2 = DocumentSearchResult(
            documentId = 42L,
            documentName = "Assignment1.pdf",
            pageIndex = 3,
            context = "Here is the theorem on page 4",
            source = DocumentSearchResult.Source.OCR_TEXT
        )
        assertEquals(3, result2.pageIndex)
        assertEquals(DocumentSearchResult.Source.OCR_TEXT, result2.source)
    }

    @Test
    fun searchUiState_defaultsAndTransitions() {
        val defaultState = SearchUiState()
        assertEquals("", defaultState.query)
        assertTrue(defaultState.results.isEmpty())
        assertFalse(defaultState.isSearching)
        assertNull(defaultState.progress)
        assertFalse(defaultState.hasSearched)
        assertNull(defaultState.errorMessage)

        val searchingState = defaultState.copy(
            query = "Physics",
            isSearching = true,
            progress = "Indexing 1/2",
            hasSearched = true
        )
        assertEquals("Physics", searchingState.query)
        assertTrue(searchingState.isSearching)
        assertEquals("Indexing 1/2", searchingState.progress)
        assertTrue(searchingState.hasSearched)
    }

    @Test
    fun documentSort_enumValues() {
        val sorts = DocumentSort.values()
        assertEquals(3, sorts.size)
        assertTrue(sorts.contains(DocumentSort.RECENT))
        assertTrue(sorts.contains(DocumentSort.NAME))
        assertTrue(sorts.contains(DocumentSort.PAGES))
    }

    @Test
    fun documentFileStore_deleteNonExistentReturnsSuccess() {
        val store = DocumentFileStore()
        val dummyDoc = Document(
            id = 999L,
            name = "NonExistent.pdf",
            pdfPath = File(System.getProperty("java.io.tmpdir"), "non_existent_12345.pdf").absolutePath,
            thumbnailPath = null,
            pageCount = 1,
            size = 0L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        assertFalse(store.documentExists(dummyDoc))
        val result = store.deleteDocumentFiles(dummyDoc)
        assertTrue(result.isSuccess)
    }

    @Test
    fun documentRepository_studentSubjectIcon() {
        assertEquals("school", DocumentRepository.STUDENT_SUBJECT_ICON)
    }
}
