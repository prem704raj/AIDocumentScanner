package com.example.aidocumentscanner.storage

import com.example.aidocumentscanner.data.Document

interface DocumentFiles {
    fun deleteDocumentFiles(
        document: Document
    ): Result<Unit>

    fun documentExists(
        document: Document
    ): Boolean
}
