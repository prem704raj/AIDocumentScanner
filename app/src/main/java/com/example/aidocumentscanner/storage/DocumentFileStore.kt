package com.example.aidocumentscanner.storage

import com.example.aidocumentscanner.data.Document
import java.io.File

class DocumentFileStore : DocumentFiles {

    override fun deleteDocumentFiles(
        document: Document
    ): Result<Unit> =
        runCatching {
            deleteRequiredFile(
                File(document.pdfPath),
                "PDF"
            )

            document.thumbnailPath
                ?.takeIf(String::isNotBlank)
                ?.let { path ->
                    deleteOptionalFile(
                        File(path),
                        "thumbnail"
                    )
                }
        }

    override fun documentExists(
        document: Document
    ): Boolean =
        File(document.pdfPath).isFile

    private fun deleteRequiredFile(
        file: File,
        label: String
    ) {
        if (!file.exists()) return

        check(file.delete()) {
            "Could not delete $label"
        }
    }

    private fun deleteOptionalFile(
        file: File,
        label: String
    ) {
        if (!file.exists()) return

        check(file.delete()) {
            "Could not delete $label"
        }
    }
}
