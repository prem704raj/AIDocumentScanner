package com.example.aidocumentscanner.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Migration regression test that bootstraps real legacy SQLite schemas and then
 * asks the production RoomDatabase + production migrations to open them.
 *
 * This is valuable even though Phase 10 also enables schema export:
 * versions 1/2 predate committed Room schema JSON in this project.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {

    private val context =
        ApplicationProvider
            .getApplicationContext<
                android.content.Context
            >()

    private val dbName =
        "migration_phase10_test.db"

    private val dbFile:
        File
        get() =
            context.getDatabasePath(
                dbName
            )

    @Before
    fun before() {
        context.deleteDatabase(dbName)
    }

    @After
    fun after() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration_1_to_3_preservesDocumentAndAddsFields() =
        runBlocking {
            createVersion1Database()

            val db =
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(
                        *AppDatabase
                            .ALL_MIGRATIONS
                    )
                    .build()

            try {
                val document =
                    db.documentDao()
                        .getDocumentById(1L)

                requireNotNull(document)

                assertEquals(
                    "Legacy",
                    document.name
                )
                assertEquals(
                    3,
                    document.pageCount
                )
                assertNull(
                    document.folderId
                )
                assertNull(
                    document.documentType
                )
                assertNull(
                    document.extractedText
                )
                assertFalse(
                    document
                        .isOcrProcessed
                )
                assertNull(
                    document.emoji
                )

                // Force folder-table query to validate its creation too.
                db.folderDao()
                    .getFolderById(1L)
            } finally {
                db.close()
            }
        }

    @Test
    fun migration_2_to_3_preservesOcrAndAddsEmoji() =
        runBlocking {
            createVersion2Database()

            val db =
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(
                        *AppDatabase
                            .ALL_MIGRATIONS
                    )
                    .build()

            try {
                val document =
                    db.documentDao()
                        .getDocumentById(1L)

                requireNotNull(document)

                assertEquals(
                    "saved OCR",
                    document.extractedText
                )
                assertEquals(
                    true,
                    document.isOcrProcessed
                )
                assertNull(document.emoji)
            } finally {
                db.close()
            }
        }

    private fun createVersion1Database() {
        dbFile.parentFile?.mkdirs()

        val db =
            SQLiteDatabase
                .openOrCreateDatabase(
                    dbFile,
                    null
                )

        db.use {
            it.execSQL(
                """
                CREATE TABLE documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    pdfPath TEXT NOT NULL,
                    thumbnailPath TEXT,
                    pageCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    size INTEGER NOT NULL
                )
                """.trimIndent()
            )

            it.execSQL(
                """
                INSERT INTO documents
                (id, name, pdfPath, thumbnailPath, pageCount, createdAt, updatedAt, size)
                VALUES
                (1, 'Legacy', '/tmp/legacy.pdf', NULL, 3, 100, 200, 1234)
                """.trimIndent()
            )

            it.version = 1
        }
    }

    private fun createVersion2Database() {
        dbFile.parentFile?.mkdirs()

        val db =
            SQLiteDatabase
                .openOrCreateDatabase(
                    dbFile,
                    null
                )

        db.use {
            it.execSQL(
                """
                CREATE TABLE documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    pdfPath TEXT NOT NULL,
                    thumbnailPath TEXT,
                    pageCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    size INTEGER NOT NULL,
                    folderId INTEGER DEFAULT NULL,
                    documentType TEXT DEFAULT NULL,
                    extractedText TEXT DEFAULT NULL,
                    isOcrProcessed INTEGER NOT NULL DEFAULT 0,
                    lastSharedAt INTEGER DEFAULT NULL,
                    lastViewedAt INTEGER DEFAULT NULL
                )
                """.trimIndent()
            )

            it.execSQL(
                """
                CREATE TABLE folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT 'folder',
                    color INTEGER NOT NULL DEFAULT 4284704497,
                    isSystemFolder INTEGER NOT NULL DEFAULT 0,
                    documentCount INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            it.execSQL(
                """
                INSERT INTO documents
                (id, name, pdfPath, thumbnailPath, pageCount, createdAt, updatedAt, size,
                 folderId, documentType, extractedText, isOcrProcessed, lastSharedAt, lastViewedAt)
                VALUES
                (1, 'OCR Legacy', '/tmp/ocr.pdf', NULL, 1, 100, 200, 500,
                 NULL, NULL, 'saved OCR', 1, NULL, NULL)
                """.trimIndent()
            )

            it.version = 2
        }
    }
}
