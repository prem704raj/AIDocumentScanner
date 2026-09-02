package com.example.aidocumentscanner.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aidocumentscanner.domain.search.DocumentSearchEngine
import com.example.aidocumentscanner.domain.search.DocumentSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results:
        List<DocumentSearchResult> =
        emptyList(),
    val isSearching: Boolean = false,
    val progress: String? = null,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

class SearchViewModel(
    private val searchEngine:
        DocumentSearchEngine
) : ViewModel() {

    private val _state =
        MutableStateFlow(
            SearchUiState()
        )

    val state:
        StateFlow<SearchUiState> =
        _state.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(value: String) {
        _state.update {
            it.copy(
                query = value,
                errorMessage = null
            )
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _state.value =
            SearchUiState()
    }

    fun dismissError() {
        _state.update {
            it.copy(
                errorMessage = null
            )
        }
    }

    fun search() {
        val query =
            _state.value
                .query
                .trim()

        if (
            query.isBlank() ||
            _state.value.isSearching
        ) {
            return
        }

        searchJob?.cancel()

        searchJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isSearching = true,
                        progress = "Searching…",
                        hasSearched = true,
                        errorMessage = null
                    )
                }

                runCatching {
                    searchEngine.search(
                        query = query
                    ) { progress ->
                        _state.update {
                            it.copy(
                                progress =
                                    progress
                            )
                        }
                    }
                }.onSuccess { results ->
                    _state.update {
                        it.copy(
                            results = results,
                            isSearching = false,
                            progress = null
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            results =
                                emptyList(),
                            isSearching =
                                false,
                            progress = null,
                            errorMessage =
                                error.message
                                    ?: "Search failed"
                        )
                    }
                }
            }
    }

    class Factory(
        private val searchEngine:
            DocumentSearchEngine
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            require(
                modelClass.isAssignableFrom(
                    SearchViewModel::
                        class.java
                )
            )

            return SearchViewModel(
                searchEngine
            ) as T
        }
    }
}
