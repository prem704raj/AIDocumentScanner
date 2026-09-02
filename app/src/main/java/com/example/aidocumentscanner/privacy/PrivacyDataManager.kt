package com.example.aidocumentscanner.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.aidocumentscanner.data.AppDatabase
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.scanner.StudentModeManager
import com.example.aidocumentscanner.util.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Audits and erases data that DocuScan itself manages.
 * It does not claim control over recipient-app copies or historical public exports that
 * existed before ExportRegistry was introduced.
 */
object PrivacyDataManager {

    data class Snapshot(
        val documentCount: Int,
        val indexedOcrDocuments: Int,
        val studyDocumentCount: Int,
        val subjectCount: Int,
        val internalPdfBytes: Long,
        val thumbnailBytes: Long,
        val toolOutputBytes: Long,
        val crashLogBytes: Long,
        val temporaryCacheBytes: Long,
        val databaseBytes: Long,
        val crashReportCount: Int,
        val trackedPublicCopies: Int,
        val cameraPermissionGranted: Boolean
    ) {
        val totalAppManagedBytes: Long
            get() = internalPdfBytes + thumbnailBytes + toolOutputBytes +
                crashLogBytes + temporaryCacheBytes + databaseBytes
    }

    data class EraseResult(
        val publicCopiesDeletedOrMissing: Int,
        val publicCopiesFailed: Int,
        val errors: List<String>
    ) {
        val success: Boolean
            get() = errors.isEmpty() && publicCopiesFailed == 0
    }

    suspend fun snapshot(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val repository = DocumentRepository(appContext)
        val documents = repository.getAllDocuments().first()
        val subjects = repository.getStudentSubjects().first()

        Snapshot(
            documentCount = documents.size,
            indexedOcrDocuments = documents.count { it.isOcrProcessed },
            studyDocumentCount = documents.count {
                it.documentType?.startsWith("student:") == true
            },
            subjectCount = subjects.size,
            internalPdfBytes = directoryBytes(File(appContext.filesDir, "documents")),
            thumbnailBytes = directoryBytes(File(appContext.filesDir, "thumbnails")),
            toolOutputBytes = directoryBytes(File(appContext.filesDir, "pdf_tools")) +
                directoryBytes(File(appContext.filesDir, "exports")),
            crashLogBytes = directoryBytes(File(appContext.filesDir, "crashes")),
            temporaryCacheBytes = directoryBytes(appContext.cacheDir),
            databaseBytes = databaseBytes(appContext),
            crashReportCount = File(appContext.filesDir, "crashes")
                .listFiles()?.count { it.isFile && it.extension == "txt" } ?: 0,
            trackedPublicCopies = ExportRegistry.trackedCount(appContext),
            cameraPermissionGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    suspend fun eraseAllManagedData(
        context: Context,
        deleteTrackedPublicCopies: Boolean
    ): EraseResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val errors = mutableListOf<String>()

        val publicResult = if (deleteTrackedPublicCopies) {
            ExportRegistry.deleteTrackedPublicCopies(appContext)
        } else {
            ExportRegistry.DeleteResult(0, 0)
        }

        runCatching {
            AppDatabase.getDatabase(appContext).clearAllTables()
        }.onFailure {
            errors += "Could not clear local database"
        }

        listOf(
            File(appContext.filesDir, "documents"),
            File(appContext.filesDir, "thumbnails"),
            File(appContext.filesDir, "pdf_tools"),
            File(appContext.filesDir, "exports"),
            File(appContext.filesDir, "crashes")
        ).forEach { directory ->
            runCatching { deleteDirectoryContents(directory) }
                .onFailure { errors += "Could not clear ${directory.name}" }
        }

        runCatching {
            appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }.onFailure {
            errors += "Could not clear temporary cache"
        }

        runCatching {
            appContext.getExternalFilesDir(null)?.listFiles()?.forEach {
                it.deleteRecursively()
            }
        }.onFailure {
            errors += "Could not clear app-specific external files"
        }

        runCatching { CrashReporter.clearAllCrashes(appContext) }
            .onFailure { errors += "Could not clear local crash reports" }

        runCatching { StudentModeManager.reset(appContext) }
            .onFailure { errors += "Could not reset Study Mode data" }

        runCatching {
            appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }.onFailure {
            errors += "Could not reset local PDF/storage preferences"
        }

        // Full privacy erase also forgets the export registry itself. If the user did not
        // request public-file deletion, those copies remain user-owned files outside app data.
        runCatching { ExportRegistry.clear(appContext) }
            .onFailure { errors += "Could not clear export registry" }

        EraseResult(
            publicCopiesDeletedOrMissing = publicResult.deletedOrMissing,
            publicCopiesFailed = publicResult.failed,
            errors = errors.distinct()
        )
    }

    private fun directoryBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::directoryBytes) ?: 0L
    }

    private fun databaseBytes(context: Context): Long {
        val main = context.getDatabasePath("document_scanner_db")
        val wal = File(main.absolutePath + "-wal")
        val shm = File(main.absolutePath + "-shm")
        return listOf(main, wal, shm).filter(File::exists).sumOf(File::length)
    }

    private fun deleteDirectoryContents(directory: File) {
        if (!directory.exists()) return
        directory.listFiles()?.forEach { child ->
            if (!child.deleteRecursively() && child.exists()) {
                error("Failed to delete ${child.name}")
            }
        }
    }
}
