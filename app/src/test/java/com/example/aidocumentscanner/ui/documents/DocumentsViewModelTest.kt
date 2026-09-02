package com.example.aidocumentscanner.ui.documents

import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentStore
import com.example.aidocumentscanner.storage.DocumentFiles
import com.example.aidocumentscanner.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelTest {

    @get:Rule
    val mainDispatcherRule =
        MainDispatcherRule()

    @Test
    fun queryAndSort_areDerivedFromRepositoryFlow() =
        runTest(
            mainDispatcherRule.dispatcher
        ) {
            val store =
                FakeDocumentStore(
                    listOf(
                        doc(
                            1,
                            "Zeta",
                            pages = 2,
                            updated = 10
                        ),
                        doc(
                            2,
                            "Alpha",
                            pages = 9,
                            updated = 20
                        )
                    )
                )

            val viewModel =
                DocumentsViewModel(
                    store,
                    FakeDocumentFiles()
                )

            advanceUntilIdle()

            viewModel.setSort(
                DocumentSort.NAME
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "Alpha",
                    "Zeta"
                ),
                viewModel.state.value
                    .visibleDocuments
                    .map { it.name }
            )

            viewModel.setQuery("zet")
            advanceUntilIdle()

            assertEquals(
                listOf("Zeta"),
                viewModel.state.value
                    .visibleDocuments
                    .map { it.name }
            )
        }

    @Test
    fun delete_doesNotDeleteRowWhenFileDeletionFails() =
        runTest(
            mainDispatcherRule.dispatcher
        ) {
            val document =
                doc(
                    1,
                    "Keep",
                    pages = 1,
                    updated = 1
                )

            val store =
                FakeDocumentStore(
                    listOf(document)
                )

            val files =
                FakeDocumentFiles(
                    shouldFail = true
                )

            val viewModel =
                DocumentsViewModel(
                    store,
                    files
                )

            viewModel.delete(document)
            advanceUntilIdle()

            assertTrue(
                store.documents.value
                    .contains(document)
            )
            assertFalse(
                store.deleteCalled
            )
        }

    @Test
    fun rename_delegatesToStore() =
        runTest(
            mainDispatcherRule.dispatcher
        ) {
            val document =
                doc(
                    1,
                    "Old",
                    pages = 1,
                    updated = 1
                )

            val store =
                FakeDocumentStore(
                    listOf(document)
                )

            val viewModel =
                DocumentsViewModel(
                    store,
                    FakeDocumentFiles()
                )

            viewModel.rename(
                document.id,
                "New"
            )
            advanceUntilIdle()

            assertEquals(
                "New",
                store.documents.value
                    .single()
                    .name
            )
        }

    private fun doc(
        id: Long,
        name: String,
        pages: Int,
        updated: Long
    ) =
        Document(
            id = id,
            name = name,
            pdfPath =
                "/tmp/$id.pdf",
            thumbnailPath = null,
            pageCount = pages,
            updatedAt = updated
        )

    private class FakeDocumentStore(
        initial:
            List<Document>
    ) : DocumentStore {

        val documents =
            MutableStateFlow(initial)

        var deleteCalled = false

        override fun getAllDocuments():
            Flow<List<Document>> =
            documents

        override suspend fun updateOcrText(
            documentId: Long,
            encodedText: String
        ) = Unit

        override suspend fun renameDocument(
            documentId: Long,
            newName: String
        ) {
            documents.value =
                documents.value.map {
                    if (
                        it.id == documentId
                    ) {
                        it.copy(
                            name = newName
                        )
                    } else {
                        it
                    }
                }
        }

        override suspend fun deleteDocument(
            document: Document
        ) {
            deleteCalled = true
            documents.value =
                documents.value
                    .filterNot {
                        it.id ==
                            document.id
                    }
        }

        override suspend fun markShared(
            documentId: Long
        ) = Unit
    }

    private class FakeDocumentFiles(
        private val shouldFail:
            Boolean = false
    ) : DocumentFiles {

        override fun deleteDocumentFiles(
            document: Document
        ): Result<Unit> =
            if (shouldFail) {
                Result.failure(
                    IllegalStateException(
                        "file locked"
                    )
                )
            } else {
                Result.success(Unit)
            }

        override fun documentExists(
            document: Document
        ): Boolean = true
    }
}
