package com.example.aidocumentscanner.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.storage.DocumentFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DocumentSort {
    RECENT,
    NAME,
    PAGES
}

data class DocumentsUiState(
    val query: String = "",
    val sort: DocumentSort = DocumentSort.RECENT,
    val message: String? = null
)

class DocumentsViewModel(
    private val repository: DocumentRepository,
    private val fileStore: DocumentFileStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    val allDocuments: StateFlow<List<Document>> = repository.getAllDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val visibleDocuments: StateFlow<List<Document>> = combine(
        allDocuments,
        _uiState
    ) { documents, state ->
        val filtered = if (state.query.isBlank()) {
            documents
        } else {
            documents.filter {
                it.name.contains(state.query, ignoreCase = true)
            }
        }

        when (state.sort) {
            DocumentSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
            DocumentSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            DocumentSort.PAGES -> filtered.sortedByDescending { it.pageCount }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun setSort(value: DocumentSort) {
        _uiState.update { it.copy(sort = value) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun renameDocument(documentId: Long, newName: String) {
        viewModelScope.launch {
            runCatching {
                repository.renameDocument(documentId, newName)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(message = error.message ?: "Could not rename document")
                }
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            val fileResult = fileStore.deleteDocumentFiles(document)
            if (fileResult.isFailure) {
                _uiState.update {
                    it.copy(
                        message = "Could not delete document file: ${fileResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            runCatching {
                repository.deleteDocument(document)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(message = "Could not remove document record: ${error.message}")
                }
            }
        }
    }

    fun markShared(documentId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.markShared(documentId)
            }
        }
    }

    class Factory(
        private val repository: DocumentRepository,
        private val fileStore: DocumentFileStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DocumentsViewModel::class.java))
            return DocumentsViewModel(repository, fileStore) as T
        }
    }
}
