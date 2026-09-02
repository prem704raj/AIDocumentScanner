package com.example.aidocumentscanner.domain.search

import android.content.Context
import com.example.aidocumentscanner.data.DocumentStore
import com.example.aidocumentscanner.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class DocumentSearchResult(
    val documentId: Long,
    val documentName: String,
    val pageIndex: Int,
    val context: String,
    val source: Source
) {
    enum class Source {
        DOCUMENT_NAME,
        OCR_TEXT
    }
}

/**
 * ViewModel-facing contract.
 * Tests can provide a deterministic fake without Android/PDF/ML Kit.
 */
interface DocumentSearchEngine {
    suspend fun search(
        query: String,
        onProgress: (String) -> Unit = {}
    ): List<DocumentSearchResult>
}

class SearchDocumentsUseCase(
    context: Context,
    private val repository:
        DocumentStore
) : DocumentSearchEngine {

    private val appContext =
        context.applicationContext

    override suspend fun search(
        query: String,
        onProgress: (String) -> Unit
    ): List<DocumentSearchResult> =
        withContext(Dispatchers.IO) {
            val needle = query.trim()

            require(needle.isNotBlank()) {
                "Search query is empty"
            }

            val documents =
                repository
                    .getAllDocuments()
                    .first()

            buildList {
                documents.forEachIndexed {
                        index,
                        document ->

                    if (
                        document.name.contains(
                            needle,
                            ignoreCase = true
                        )
                    ) {
                        add(
                            DocumentSearchResult(
                                documentId =
                                    document.id,
                                documentName =
                                    document.name,
                                pageIndex = 0,
                                context =
                                    "Document name: ${document.name}",
                                source =
                                    DocumentSearchResult
                                        .Source
                                        .DOCUMENT_NAME
                            )
                        )
                    }

                    val pages =
                        if (
                            document
                                .isOcrProcessed
                        ) {
                            OcrEngine
                                .decodePersisted(
                                    document
                                        .extractedText
                                )
                        } else {
                            onProgress(
                                "Indexing ${index + 1}/${documents.size}"
                            )

                            val extracted =
                                OcrEngine
                                    .extractTextFromPdf(
                                        appContext,
                                        document.pdfPath
                                    ) {
                                            current,
                                            total ->
                                        onProgress(
                                            "Indexing ${index + 1}/${documents.size} • page $current/$total"
                                        )
                                    }

                            repository.updateOcrText(
                                document.id,
                                OcrEngine
                                    .encodeForPersistence(
                                        extracted
                                    )
                            )

                            extracted
                        }

                    OcrEngine.searchKeyword(
                        pagesText = pages,
                        keyword = needle,
                        caseSensitive = false
                    ).forEach { match ->
                        add(
                            DocumentSearchResult(
                                documentId =
                                    document.id,
                                documentName =
                                    document.name,
                                pageIndex =
                                    match.pageIndex,
                                context =
                                    match.context,
                                source =
                                    DocumentSearchResult
                                        .Source
                                        .OCR_TEXT
                            )
                        )
                    }
                }
            }
        }
}
