package com.example.aidocumentscanner.storage

import com.example.aidocumentscanner.data.Document
import java.io.File

/**
 * Filesystem boundary for persisted document files.
 *
 * PdfGenerator creates PDFs; it should not also become the generic document-delete service.
 */
class DocumentFileStore {

    fun documentExists(document: Document): Boolean =
        File(document.pdfPath).isFile

    fun deleteDocumentFiles(document: Document): Result<Unit> =
        runCatching {
            deleteIfPresent(
                file = File(document.pdfPath),
                label = "PDF"
            )

            document.thumbnailPath
                ?.takeIf(String::isNotBlank)
                ?.let { path ->
                    deleteIfPresent(
                        file = File(path),
                        label = "thumbnail"
                    )
                }
        }

    private fun deleteIfPresent(
        file: File,
        label: String
    ) {
        if (!file.exists()) return
        check(file.delete()) {
            "Could not delete $label"
        }
    }
}
