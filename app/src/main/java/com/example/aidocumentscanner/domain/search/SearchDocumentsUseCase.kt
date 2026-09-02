package com.example.aidocumentscanner.domain.search

import android.content.Context
import com.example.aidocumentscanner.data.DocumentRepository
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
 * Global search business logic.
 *
 * The UI does not decide when/how to OCR or persist text anymore.
 */
class SearchDocumentsUseCase(
    context: Context,
    private val repository: DocumentRepository
) {
    private val appContext = context.applicationContext

    suspend operator fun invoke(
        query: String,
        onProgress: (String) -> Unit = {}
    ): List<DocumentSearchResult> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        require(needle.isNotBlank()) {
            "Search query is empty"
        }

        val documents = repository.getAllDocuments().first()
        val output = mutableListOf<DocumentSearchResult>()

        documents.forEachIndexed { index, document ->
            if (document.name.contains(needle, ignoreCase = true)) {
                output += DocumentSearchResult(
                    documentId = document.id,
                    documentName = document.name,
                    pageIndex = 0,
                    context = "Document name: ${document.name}",
                    source = DocumentSearchResult.Source.DOCUMENT_NAME
                )
            }

            val pages = if (document.isOcrProcessed) {
                OcrEngine.decodePersisted(document.extractedText)
            } else {
                onProgress("Indexing ${index + 1}/${documents.size}")

                val extracted = OcrEngine.extractTextFromPdf(
                    appContext,
                    document.pdfPath
                ) { current, total ->
                    onProgress(
                        "Indexing ${index + 1}/${documents.size} • page $current/$total"
                    )
                }

                repository.updateOcrText(
                    document.id,
                    OcrEngine.encodeForPersistence(extracted)
                )
                extracted
            }

            OcrEngine.searchKeyword(
                pagesText = pages,
                keyword = needle,
                caseSensitive = false
            ).forEach { match ->
                output += DocumentSearchResult(
                    documentId = document.id,
                    documentName = document.name,
                    pageIndex = match.pageIndex,
                    context = match.context,
                    source = DocumentSearchResult.Source.OCR_TEXT
                )
            }
        }

        output
    }
}
