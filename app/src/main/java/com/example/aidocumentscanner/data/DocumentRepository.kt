package com.example.aidocumentscanner.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DocumentRepository(context: Context) {
    private val documentDao = AppDatabase.getDatabase(context).documentDao()

    fun getAllDocuments(): Flow<List<Document>> = documentDao.getAllDocuments()

    fun getRecentDocuments(limit: Int = 10): Flow<List<Document>> =
        documentDao.getRecentDocuments(limit)

    suspend fun getDocumentById(id: Long): Document? =
        documentDao.getDocumentById(id)

    fun searchDocuments(query: String): Flow<List<Document>> =
        documentDao.searchDocuments(query)

    suspend fun insertDocument(document: Document): Long =
        documentDao.insertDocument(document)

    suspend fun updateDocument(document: Document) =
        documentDao.updateDocument(document)

    suspend fun deleteDocument(document: Document) =
        documentDao.deleteDocument(document)

    suspend fun deleteDocumentById(id: Long) =
        documentDao.deleteDocumentById(id)

    suspend fun getDocumentCount(): Int =
        documentDao.getDocumentCount()

    suspend fun renameDocument(documentId: Long, newName: String) =
        documentDao.renameDocument(
            documentId,
            newName,
            System.currentTimeMillis()
        )

    suspend fun updateEmoji(documentId: Long, emoji: String?) =
        documentDao.updateEmoji(
            documentId,
            emoji,
            System.currentTimeMillis()
        )

    suspend fun updateOcrText(documentId: Long, encodedText: String) =
        documentDao.updateOcrText(documentId, encodedText)

    suspend fun markViewed(documentId: Long) =
        documentDao.updateLastViewed(documentId, System.currentTimeMillis())

    suspend fun markShared(documentId: Long) =
        documentDao.updateLastShared(documentId, System.currentTimeMillis())
}
