package com.example.aidocumentscanner.ui.search

import com.example.aidocumentscanner.domain.search.DocumentSearchEngine
import com.example.aidocumentscanner.domain.search.DocumentSearchResult
import com.example.aidocumentscanner.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule =
        MainDispatcherRule()

    @Test
    fun search_updatesProgressAndResults() =
        runTest(
            mainDispatcherRule.dispatcher
        ) {
            val engine =
                object :
                    DocumentSearchEngine {
                    override suspend fun search(
                        query: String,
                        onProgress:
                            (String) -> Unit
                    ):
                        List<DocumentSearchResult> {
                        onProgress(
                            "Indexing 1/1"
                        )
                        return listOf(
                            DocumentSearchResult(
                                documentId = 7,
                                documentName =
                                    "DBMS Notes",
                                pageIndex = 3,
                                context =
                                    "normalization",
                                source =
                                    DocumentSearchResult
                                        .Source
                                        .OCR_TEXT
                            )
                        )
                    }
                }

            val viewModel =
                SearchViewModel(engine)

            viewModel.setQuery(
                "normalization"
            )
            viewModel.search()

            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertTrue(
                state.hasSearched
            )
            assertFalse(
                state.isSearching
            )
            assertEquals(
                1,
                state.results.size
            )
            assertEquals(
                3,
                state.results.single()
                    .pageIndex
            )
        }

    @Test
    fun search_failure_isExposedAsUiError() =
        runTest(
            mainDispatcherRule.dispatcher
        ) {
            val engine =
                object :
                    DocumentSearchEngine {
                    override suspend fun search(
                        query: String,
                        onProgress:
                            (String) -> Unit
                    ):
                        List<DocumentSearchResult> {
                        error("broken pdf")
                    }
                }

            val viewModel =
                SearchViewModel(engine)

            viewModel.setQuery("x")
            viewModel.search()

            advanceUntilIdle()

            assertEquals(
                "broken pdf",
                viewModel.state.value
                    .errorMessage
            )
            assertFalse(
                viewModel.state.value
                    .isSearching
            )
        }

    @Test
    fun blankQuery_doesNotStartSearch() =
        runTest(
            mainDispatcherRule.dispatcher
        ) {
            var called = false

            val engine =
                object :
                    DocumentSearchEngine {
                    override suspend fun search(
                        query: String,
                        onProgress:
                            (String) -> Unit
                    ):
                        List<DocumentSearchResult> {
                        called = true
                        return emptyList()
                    }
                }

            val viewModel =
                SearchViewModel(engine)

            viewModel.setQuery("   ")
            viewModel.search()

            advanceUntilIdle()

            assertFalse(called)
        }
}
