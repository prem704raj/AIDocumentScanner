package com.example.aidocumentscanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<Document>>
    
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int): Flow<List<Document>>
    
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): Document?
    
    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' OR extractedText LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<Document>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long
    
    @Update
    suspend fun updateDocument(document: Document)
    
    @Delete
    suspend fun deleteDocument(document: Document)
    
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
    
    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int
    
    // Folder filtering
    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getDocumentsByFolder(folderId: Long): Flow<List<Document>>
    
    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY updatedAt DESC")
    fun getUncategorizedDocuments(): Flow<List<Document>>
    
    // History tracking
    @Query("SELECT * FROM documents WHERE lastSharedAt IS NOT NULL ORDER BY lastSharedAt DESC LIMIT :limit")
    fun getRecentlyShared(limit: Int): Flow<List<Document>>
    
    @Query("SELECT * FROM documents WHERE lastViewedAt IS NOT NULL ORDER BY lastViewedAt DESC LIMIT :limit")
    fun getRecentlyViewed(limit: Int): Flow<List<Document>>
    
    @Query("UPDATE documents SET lastSharedAt = :timestamp WHERE id = :documentId")
    suspend fun updateLastShared(documentId: Long, timestamp: Long)
    
    @Query("UPDATE documents SET lastViewedAt = :timestamp WHERE id = :documentId")
    suspend fun updateLastViewed(documentId: Long, timestamp: Long)
    
    // OCR processing
    @Query("SELECT * FROM documents WHERE isOcrProcessed = 0 ORDER BY createdAt DESC")
    fun getUnprocessedDocuments(): Flow<List<Document>>
    
    @Query("UPDATE documents SET extractedText = :text, isOcrProcessed = 1 WHERE id = :documentId")
    suspend fun updateOcrText(documentId: Long, text: String)
    
    // Folder assignment
    @Query("UPDATE documents SET folderId = :folderId, documentType = :documentType WHERE id = :documentId")
    suspend fun assignToFolder(documentId: Long, folderId: Long?, documentType: String?)
    
    // Rename document
    @Query("UPDATE documents SET name = :newName, updatedAt = :timestamp WHERE id = :documentId")
    suspend fun renameDocument(documentId: Long, newName: String, timestamp: Long)
    
    // Update emoji
    @Query("UPDATE documents SET emoji = :emoji, updatedAt = :timestamp WHERE id = :documentId")
    suspend fun updateEmoji(documentId: Long, emoji: String?, timestamp: Long)
}
