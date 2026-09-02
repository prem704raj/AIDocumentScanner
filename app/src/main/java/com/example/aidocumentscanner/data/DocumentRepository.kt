package com.example.aidocumentscanner.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

/**
 * Room-backed document repository.
 *
 * The primary constructor is injectable. The Context constructor remains temporarily
 * so older Phase-1..8 screens can migrate incrementally rather than through a flag-day rewrite.
 */
class DocumentRepository(
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao
) : DocumentStore {
    constructor(context: Context) : this(
        documentDao = AppDatabase
            .getDatabase(context.applicationContext)
            .documentDao(),
        folderDao = AppDatabase
            .getDatabase(context.applicationContext)
            .folderDao()
    )

    override fun getAllDocuments(): Flow<List<Document>> =
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

    override suspend fun deleteDocument(document: Document) {
        val folderId = document.folderId
        documentDao.deleteDocument(document)
        folderId?.let { folderDao.updateDocumentCount(it) }
    }

    suspend fun deleteDocumentById(id: Long) {
        val folderId = documentDao.getDocumentById(id)?.folderId
        documentDao.deleteDocumentById(id)
        folderId?.let { folderDao.updateDocumentCount(it) }
    }

    suspend fun getDocumentCount(): Int =
        documentDao.getDocumentCount()

    override suspend fun renameDocument(documentId: Long, newName: String) {
        val clean = newName.trim().take(100)
        require(clean.isNotBlank()) {
            "Document name cannot be empty"
        }
        documentDao.renameDocument(
            documentId,
            clean,
            System.currentTimeMillis()
        )
    }

    suspend fun updateEmoji(documentId: Long, emoji: String?) =
        documentDao.updateEmoji(
            documentId,
            emoji,
            System.currentTimeMillis()
        )

    override suspend fun updateOcrText(documentId: Long, encodedText: String) =
        documentDao.updateOcrText(documentId, encodedText)

    suspend fun markViewed(documentId: Long) =
        documentDao.updateLastViewed(
            documentId,
            System.currentTimeMillis()
        )

    override suspend fun markShared(documentId: Long) =
        documentDao.updateLastShared(
            documentId,
            System.currentTimeMillis()
        )

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
        require(clean.isNotBlank()) {
            "Subject name cannot be empty"
        }

        val existing = folderDao.getFolderByName(clean)
        if (existing != null && existing.icon == STUDENT_SUBJECT_ICON) {
            return existing.id
        }

        val color = SUBJECT_COLORS[
            clean.lowercase().hashCode().absoluteValue % SUBJECT_COLORS.size
        ]

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
