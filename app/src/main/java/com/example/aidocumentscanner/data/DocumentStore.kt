package com.example.aidocumentscanner.data

import kotlinx.coroutines.flow.Flow

/**
 * Narrow interface used by ViewModels/use-cases.
 *
 * It deliberately contains only operations required by Phase-9/10 presentation
 * and search logic. The concrete repository may expose additional feature APIs.
 */
interface DocumentStore {
    fun getAllDocuments(): Flow<List<Document>>

    suspend fun updateOcrText(
        documentId: Long,
        encodedText: String
    )

    suspend fun renameDocument(
        documentId: Long,
        newName: String
    )

    suspend fun deleteDocument(
        document: Document
    )

    suspend fun markShared(
        documentId: Long
    )
}
