package com.example.aidocumentscanner.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Handles temporary copies of user-selected PDFs without broad storage permissions.
 */
object PdfToolFileManager {
    private const val MAX_EXTERNAL_PDF_BYTES = 300L * 1024L * 1024L

    data class ExternalInfo(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long?
    )

    fun queryExternalInfo(context: Context, uri: Uri): ExternalInfo {
        var name = "Selected.pdf"
        var size: Long? = null

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)?.takeIf(String::isNotBlank)
                        ?: name
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return ExternalInfo(
            uri = uri,
            displayName = sanitizePdfName(name),
            sizeBytes = size
        )
    }

    fun copyToToolCache(
        context: Context,
        source: ExternalInfo
    ): File {
        source.sizeBytes?.let {
            require(it <= MAX_EXTERNAL_PDF_BYTES) {
                "Selected PDF is larger than 300 MB"
            }
        }

        val dir = File(context.cacheDir, "pdf_tools")
        require(dir.exists() || dir.mkdirs()) { "Could not create PDF cache" }

        val output = File(
            dir,
            "${UUID.randomUUID()}_${sanitizePdfName(source.displayName)}"
        )

        val input = context.contentResolver.openInputStream(source.uri)
            ?: error("Could not open selected PDF")

        input.use { sourceStream ->
            FileOutputStream(output).use { destination ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = sourceStream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_EXTERNAL_PDF_BYTES) {
                        "Selected PDF exceeds the 300 MB safety limit"
                    }
                    destination.write(buffer, 0, read)
                }
            }
        }

        require(output.length() > 0L) { "Selected PDF is empty" }
        return output
    }

    fun sanitizePdfName(value: String): String {
        val safe = value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .take(100)
            .ifBlank { "Document.pdf" }

        return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
    }

    fun cleanup(file: File?) {
        if (file != null && file.parentFile?.name == "pdf_tools") {
            runCatching { file.delete() }
        }
    }
}
