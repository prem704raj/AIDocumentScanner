package com.example.aidocumentscanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.aidocumentscanner.privacy.ExportRegistry
import com.example.aidocumentscanner.ui.screens.SettingsPreferences
import com.example.aidocumentscanner.ui.screens.StorageLocation
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Internal PDF is authoritative. Public copies are optional user-configured exports and are
 * tracked only after a complete Android 10+ MediaStore write succeeds.
 */
object PdfGenerator {
    private const val TAG = "PdfGenerator"
    private const val APP_FOLDER = "DocuScan"

    enum class PageSizeType(val rectangle: Rectangle) {
        A4(PageSize.A4), LETTER(PageSize.LETTER), LEGAL(PageSize.LEGAL), FIT_IMAGE(PageSize.A4)
    }

    enum class QualityType(val jpegQuality: Int, val maxDimension: Int, val label: String) {
        STANDARD(80, 1920, "Standard (1080p)"),
        HIGH(90, 2560, "High (2K)"),
        ULTRA(100, 3840, "Ultra (4K)")
    }

    fun generatePdf(
        context: Context,
        images: List<Bitmap>,
        fileName: String,
        pageSize: PageSizeType = PageSizeType.A4,
        quality: QualityType = QualityType.HIGH
    ): String {
        require(images.isNotEmpty()) { "At least one page is required" }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFileName = "${sanitizeFileName(fileName)}_$timestamp.pdf"
        val documentsDir = File(context.filesDir, "documents")
        require(documentsDir.exists() || documentsDir.mkdirs()) {
            "Could not create document storage"
        }
        val internalFile = File(documentsDir, pdfFileName)

        generatePdfToStream(
            FileOutputStream(internalFile),
            images,
            pageSize,
            quality
        )

        val storageLocation = SettingsPreferences.getStorageLocation(context)
        if (storageLocation != StorageLocation.INTERNAL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    saveTrackedPublicCopy(
                        context,
                        internalFile,
                        pdfFileName,
                        storageLocation
                    )
                }.onFailure { error ->
                    // Never invalidate the successful internal document because an optional
                    // public copy failed.
                    Log.e(TAG, "Public copy failed", error)
                }
            } else {
                // Phase 1 removed broad storage permission. Phase 6 hides public choices on
                // API 26-28; an old stored preference must not trigger unsafe direct writes.
                Log.w(TAG, "Public copy skipped below Android 10; use Share instead")
            }
        }

        return internalFile.absolutePath
    }

    private fun generatePdfToStream(
        outputStream: OutputStream,
        images: List<Bitmap>,
        pageSize: PageSizeType,
        quality: QualityType
    ) {
        val document = Document()
        try {
            PdfWriter.getInstance(document, outputStream)
            document.open()

            images.forEachIndexed { index, bitmap ->
                require(!bitmap.isRecycled) { "Page ${index + 1} is recycled" }
                val scaled = scaleForQuality(bitmap, quality)
                val imageBytes = try {
                    ByteArrayOutputStream().use { stream ->
                        require(
                            scaled.compress(
                                Bitmap.CompressFormat.JPEG,
                                quality.jpegQuality,
                                stream
                            )
                        ) { "Could not encode page ${index + 1}" }
                        stream.toByteArray()
                    }
                } finally {
                    if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                }

                val image = Image.getInstance(imageBytes)
                if (pageSize == PageSizeType.FIT_IMAGE) {
                    document.setPageSize(Rectangle(image.width, image.height))
                    if (index > 0) document.newPage()
                    image.setAbsolutePosition(0f, 0f)
                } else {
                    if (index > 0) document.newPage()
                    image.scaleToFit(
                        pageSize.rectangle.width - 40f,
                        pageSize.rectangle.height - 40f
                    )
                    image.setAbsolutePosition(
                        (pageSize.rectangle.width - image.scaledWidth) / 2f,
                        (pageSize.rectangle.height - image.scaledHeight) / 2f
                    )
                }
                document.add(image)
            }
        } finally {
            if (document.isOpen) document.close()
            runCatching { outputStream.close() }
        }
    }

    private fun saveTrackedPublicCopy(
        context: Context,
        sourceFile: File,
        fileName: String,
        location: StorageLocation
    ) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        val relativePath = when (location) {
            StorageLocation.DOCUMENTS -> "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER"
            StorageLocation.DOWNLOADS -> "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER"
            StorageLocation.INTERNAL -> return
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Files.getContentUri("external"),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: error("Could not create public PDF copy")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not write public PDF copy")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            ExportRegistry.recordPublicCopy(context, uri)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun scaleForQuality(bitmap: Bitmap, quality: QualityType): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= quality.maxDimension) return bitmap
        val scale = quality.maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    fun generateThumbnail(context: Context, bitmap: Bitmap, documentId: String): String {
        val dir = File(context.filesDir, "thumbnails")
        require(dir.exists() || dir.mkdirs())
        val largest = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = minOf(1f, 400f / largest)
        val thumbnail = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        val file = File(dir, "thumb_$documentId.jpg")
        try {
            FileOutputStream(file).use { output ->
                require(thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, output))
            }
        } finally {
            if (thumbnail !== bitmap && !thumbnail.isRecycled) thumbnail.recycle()
        }
        return file.absolutePath
    }

    fun getFileSize(filePath: String): Long = File(filePath).length()

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun deleteDocument(pdfPath: String, thumbnailPath: String?) {
        runCatching { File(pdfPath).delete() }
        thumbnailPath?.let { runCatching { File(it).delete() } }
    }

    fun getPageCount(pdfPath: String): Int {
        var reader: PdfReader? = null
        return try {
            reader = PdfReader(pdfPath)
            reader.numberOfPages
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to read PDF", error)
            0
        } finally {
            reader?.close()
        }
    }

    private fun sanitizeFileName(value: String): String = value.trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.', ' ')
        .take(90)
        .ifBlank { "Document" }
}
