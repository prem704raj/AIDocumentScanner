package com.example.aidocumentscanner.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

class DocumentRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val documentDao = database.documentDao()
    private val folderDao = database.folderDao()

    fun getAllDocuments(): Flow<List<Document>> =
        documentDao.getAllDocuments()

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

    suspend fun deleteDocument(document: Document) {
        val folderId = document.folderId
        documentDao.deleteDocument(document)
        if (folderId != null) {
            folderDao.updateDocumentCount(folderId)
        }
    }

    suspend fun deleteDocumentById(id: Long) {
        val folderId = documentDao.getDocumentById(id)?.folderId
        documentDao.deleteDocumentById(id)
        if (folderId != null) {
            folderDao.updateDocumentCount(folderId)
        }
    }

    suspend fun getDocumentCount(): Int =
        documentDao.getDocumentCount()

    suspend fun renameDocument(documentId: Long, newName: String) =
        documentDao.renameDocument(
            documentId,
            newName.trim(),
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
        documentDao.updateLastViewed(
            documentId,
            System.currentTimeMillis()
        )

    suspend fun markShared(documentId: Long) =
        documentDao.updateLastShared(
            documentId,
            System.currentTimeMillis()
        )

    // -----------------------------------------------------------------
    // Student-mode subject/folder integration
    // -----------------------------------------------------------------

    fun getStudentSubjects(): Flow<List<Folder>> =
        folderDao.getCustomFolders().map { folders ->
            folders.filter { it.icon == STUDENT_SUBJECT_ICON }
        }

    fun getDocumentsByFolder(folderId: Long): Flow<List<Document>> =
        documentDao.getDocumentsByFolder(folderId)

    suspend fun getFolderById(folderId: Long): Folder? =
        folderDao.getFolderById(folderId)

    suspend fun createStudentSubject(name: String): Long {
        val clean = normalizeSubjectName(name)
        require(clean.isNotBlank()) { "Subject name cannot be empty" }

        val sameName = folderDao.getFolderByName(clean)
        if (sameName != null && sameName.icon == STUDENT_SUBJECT_ICON) {
            return sameName.id
        }

        val palette = SUBJECT_COLORS
        val color = palette[clean.lowercase().hashCode().absoluteValue % palette.size]

        return folderDao.insertFolder(
            Folder(
                name = clean,
                icon = STUDENT_SUBJECT_ICON,
                color = color,
                isSystemFolder = false
            )
        )
    }

    suspend fun assignStudentMetadata(
        documentId: Long,
        folderId: Long?,
        documentType: String?
    ) {
        documentDao.assignToFolder(
            documentId = documentId,
            folderId = folderId,
            documentType = documentType
        )
        folderId?.let { folderDao.updateDocumentCount(it) }
    }

    suspend fun updateFolderCount(folderId: Long) =
        folderDao.updateDocumentCount(folderId)

    private fun normalizeSubjectName(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .take(60)

    companion object {
        const val STUDENT_SUBJECT_ICON = "school"

        private val SUBJECT_COLORS = listOf(
            0xFF2563EB,
            0xFF7C3AED,
            0xFF059669,
            0xFFEA580C,
            0xFFDB2777,
            0xFF0891B2,
            0xFF4F46E5,
            0xFF65A30D
        )
    }
}
