package com.example.aidocumentscanner.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentStore
import com.example.aidocumentscanner.storage.DocumentFiles
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DocumentSort {
    RECENT,
    NAME,
    PAGES
}

data class DocumentsUiState(
    val documents:
        List<Document> =
        emptyList(),
    val visibleDocuments:
        List<Document> =
        emptyList(),
    val query: String = "",
    val sort: DocumentSort =
        DocumentSort.RECENT
)

class DocumentsViewModel(
    private val repository:
        DocumentStore,
    private val fileStore:
        DocumentFiles
) : ViewModel() {

    private val query =
        MutableStateFlow("")

    private val sort =
        MutableStateFlow(
            DocumentSort.RECENT
        )

    val state:
        StateFlow<DocumentsUiState> =
        combine(
            repository
                .getAllDocuments(),
            query,
            sort
        ) {
                documents,
                queryValue,
                sortValue ->

            val filtered =
                if (
                    queryValue.isBlank()
                ) {
                    documents
                } else {
                    documents.filter {
                        it.name.contains(
                            queryValue,
                            ignoreCase = true
                        )
                    }
                }

            val visible =
                when (sortValue) {
                    DocumentSort.RECENT ->
                        filtered
                            .sortedByDescending {
                                it.updatedAt
                            }

                    DocumentSort.NAME ->
                        filtered
                            .sortedBy {
                                it.name
                                    .lowercase()
                            }

                    DocumentSort.PAGES ->
                        filtered
                            .sortedByDescending {
                                it.pageCount
                            }
                }

            DocumentsUiState(
                documents = documents,
                visibleDocuments =
                    visible,
                query = queryValue,
                sort = sortValue
            )
        }.stateIn(
            scope = viewModelScope,

            // Eager collection makes behavior deterministic for UI and unit tests.
            started =
                SharingStarted.Eagerly,

            initialValue =
                DocumentsUiState()
        )

    private val _messages =
        MutableSharedFlow<String>(
            extraBufferCapacity = 2
        )

    val messages =
        _messages.asSharedFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(
        value: DocumentSort
    ) {
        sort.value = value
    }

    fun rename(
        documentId: Long,
        newName: String
    ) {
        viewModelScope.launch {
            runCatching {
                repository
                    .renameDocument(
                        documentId,
                        newName
                    )
            }.onFailure {
                _messages.emit(
                    it.message
                        ?: "Rename failed"
                )
            }
        }
    }

    fun delete(
        document: Document
    ) {
        viewModelScope.launch {
            val fileResult =
                fileStore
                    .deleteDocumentFiles(
                        document
                    )

            if (
                fileResult.isFailure
            ) {
                _messages.emit(
                    fileResult
                        .exceptionOrNull()
                        ?.message
                        ?: "Could not delete files"
                )
                return@launch
            }

            runCatching {
                repository
                    .deleteDocument(
                        document
                    )
            }.onFailure {
                _messages.emit(
                    it.message
                        ?: "Could not delete document record"
                )
            }
        }
    }

    fun markShared(
        documentId: Long
    ) {
        viewModelScope.launch {
            runCatching {
                repository
                    .markShared(
                        documentId
                    )
            }
        }
    }

    class Factory(
        private val repository:
            DocumentStore,
        private val fileStore:
            DocumentFiles
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            require(
                modelClass.isAssignableFrom(
                    DocumentsViewModel::
                        class.java
                )
            )

            return DocumentsViewModel(
                repository,
                fileStore
            ) as T
        }
    }
}
