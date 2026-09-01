package com.example.aidocumentscanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.aidocumentscanner.ui.screens.SettingsPreferences
import com.example.aidocumentscanner.ui.screens.StorageLocation
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfGState
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * PDF operations kept compatible with the existing UI while Phase 2 removes unbounded rendering.
 * Phase 4 will redesign the PDF-tool workflows and optimization semantics.
 */
object PdfEditor {
    private const val TAG = "PdfEditor"
    private const val APP_FOLDER = "AIDocumentScanner"
    private const val MAX_EXPORT_DIMENSION = 2400
    private const val LEGACY_THUMBNAIL_WIDTH = 180

    fun mergePdfs(
        context: Context,
        pdfPaths: List<String>,
        outputName: String
    ): Result<String> = runCatching {
        val existing = pdfPaths.map(::File).filter { it.isFile }
        require(existing.isNotEmpty()) { "No readable PDFs to merge" }

        val outputFile = createOutputFile(
            context,
            "${sanitize(outputName)}_merged_${timestamp()}.pdf"
        )

        val document = Document()
        var copy: PdfCopy? = null
        try {
            copy = PdfCopy(document, FileOutputStream(outputFile))
            document.open()
            existing.forEach { file ->
                var reader: PdfReader? = null
                try {
                    reader = PdfReader(file.absolutePath)
                    for (pageNumber in 1..reader.numberOfPages) {
                        copy.addPage(copy.getImportedPage(reader, pageNumber))
                    }
                    copy.freeReader(reader)
                } finally {
                    reader?.close()
                }
            }
        } finally {
            if (document.isOpen) document.close()
        }

        saveToPublicStorage(context, outputFile, outputFile.name)
        outputFile.absolutePath
    }

    fun splitPdf(
        context: Context,
        pdfPath: String,
        pageRanges: List<IntRange>
    ): Result<List<String>> = runCatching {
        require(pageRanges.isNotEmpty()) { "No page ranges supplied" }
        val reader = PdfReader(pdfPath)
        try {
            val totalPages = reader.numberOfPages
            val outputs = mutableListOf<String>()
            pageRanges.forEachIndexed { index, range ->
                val pages = range.filter { it in 1..totalPages }
                if (pages.isNotEmpty()) {
                    val output = createOutputFile(
                        context,
                        "${sanitize(File(pdfPath).nameWithoutExtension)}_part${index + 1}_${timestamp()}.pdf"
                    )
                    copyPages(reader, pages, output)
                    saveToPublicStorage(context, output, output.name)
                    outputs += output.absolutePath
                }
            }
            require(outputs.isNotEmpty()) { "No valid ranges to split" }
            outputs
        } finally {
            reader.close()
        }
    }

    fun removePages(
        context: Context,
        pdfPath: String,
        pagesToRemove: List<Int>
    ): Result<String> = runCatching {
        val reader = PdfReader(pdfPath)
        try {
            val removeSet = pagesToRemove.toSet()
            val keep = (1..reader.numberOfPages).filterNot(removeSet::contains)
            require(keep.isNotEmpty()) { "Cannot remove all pages" }

            val output = createOutputFile(
                context,
                "${sanitize(File(pdfPath).nameWithoutExtension)}_edited_${timestamp()}.pdf"
            )
            copyPages(reader, keep, output)
            saveToPublicStorage(context, output, output.name)
            output.absolutePath
        } finally {
            reader.close()
        }
    }

    fun extractPagesAsImages(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<Int> = runCatching {
        val file = File(pdfPath)
        require(file.isFile) { "PDF file does not exist" }

        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }

        try {
            val validPages = pagesToExtract.distinct().sorted().filter { it in 1..renderer.pageCount }
            require(validPages.isNotEmpty()) { "No valid pages to extract" }

            var saved = 0
            validPages.forEach { pageNumber ->
                renderer.openPage(pageNumber - 1).use { page ->
                    val bitmap = renderBoundedPage(page, MAX_EXPORT_DIMENSION, Bitmap.Config.ARGB_8888)
                    try {
                        val name = "${sanitize(file.nameWithoutExtension)}_page${pageNumber}_${timestamp()}.jpg"
                        if (saveImage(context, bitmap, name)) saved++
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
            saved
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    fun splitPdfByPages(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<String> = runCatching {
        val reader = PdfReader(pdfPath)
        try {
            val pages = pagesToExtract.distinct().sorted().filter { it in 1..reader.numberOfPages }
            require(pages.isNotEmpty()) { "No valid pages to extract" }

            val output = createOutputFile(
                context,
                "${sanitize(File(pdfPath).nameWithoutExtension)}_split_${timestamp()}.pdf"
            )
            copyPages(reader, pages, output)
            saveToPublicStorage(context, output, output.name)
            output.absolutePath
        } finally {
            reader.close()
        }
    }

    fun getPageCount(pdfPath: String): Int {
        var reader: PdfReader? = null
        return try {
            reader = PdfReader(pdfPath)
            reader.numberOfPages
        } catch (error: Exception) {
            Log.e(TAG, "Unable to read page count", error)
            0
        } finally {
            reader?.close()
        }
    }

    /** Render exactly one page. This is the preferred preview API. */
    fun renderPageToBitmap(
        context: Context,
        pdfPath: String,
        pageIndex: Int,
        maxWidth: Int = 1200
    ): Bitmap? {
        val file = File(pdfPath)
        if (!file.isFile) return null

        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            if (pageIndex !in 0 until renderer.pageCount) return null
            renderer.openPage(pageIndex).use { page ->
                renderPageForWidth(page, maxWidth.coerceIn(120, 1600), Bitmap.Config.RGB_565)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to render page $pageIndex", error)
            null
        } finally {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }

    /**
     * Compatibility API for the pre-Phase-4 PDF tools screen.
     * It intentionally renders tiny RGB_565 thumbnails rather than full pages. Do not use this for
     * the main viewer. Phase 4 must remove this all-pages API from PDF Tools entirely.
     */
    fun renderAllPagesToBitmap(context: Context, pdfPath: String): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        val file = File(pdfPath)
        if (!file.isFile) return result

        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            for (pageIndex in 0 until renderer.pageCount) {
                renderer.openPage(pageIndex).use { page ->
                    result += renderPageForWidth(
                        page,
                        LEGACY_THUMBNAIL_WIDTH,
                        Bitmap.Config.RGB_565
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to render legacy thumbnails", error)
            result.forEach { if (!it.isRecycled) it.recycle() }
            result.clear()
        } finally {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
        return result
    }

    fun addWatermark(
        context: Context,
        pdfPath: String,
        watermarkText: String
    ): Result<String> = runCatching {
        require(watermarkText.isNotBlank()) { "Watermark text is empty" }
        val reader = PdfReader(pdfPath)
        val output = createOutputFile(
            context,
            "${sanitize(File(pdfPath).nameWithoutExtension)}_watermarked_${timestamp()}.pdf"
        )
        var stamper: PdfStamper? = null
        try {
            stamper = PdfStamper(reader, FileOutputStream(output))
            val font = BaseFont.createFont(
                BaseFont.HELVETICA,
                BaseFont.WINANSI,
                BaseFont.NOT_EMBEDDED
            )
            for (pageNumber in 1..reader.numberOfPages) {
                val pageSize = reader.getPageSizeWithRotation(pageNumber)
                val canvas = stamper.getOverContent(pageNumber)
                canvas.saveState()
                try {
                    val state = PdfGState().apply {
                        setFillOpacity(0.30f)
                        setStrokeOpacity(0.30f)
                    }
                    canvas.setGState(state)
                    canvas.beginText()
                    canvas.setFontAndSize(font, 52f)
                    canvas.setColorFill(com.itextpdf.text.BaseColor.GRAY)
                    canvas.showTextAligned(
                        com.itextpdf.text.Element.ALIGN_CENTER,
                        watermarkText,
                        pageSize.width / 2f,
                        pageSize.height / 2f,
                        45f
                    )
                    canvas.endText()
                } finally {
                    canvas.restoreState()
                }
            }
        } finally {
            runCatching { stamper?.close() }
            reader.close()
        }
        saveToPublicStorage(context, output, output.name)
        output.absolutePath
    }

    fun passwordProtect(
        context: Context,
        pdfPath: String,
        password: String
    ): Result<String> = runCatching {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        val reader = PdfReader(pdfPath)
        val output = createOutputFile(
            context,
            "${sanitize(File(pdfPath).nameWithoutExtension)}_protected_${timestamp()}.pdf"
        )
        var stamper: PdfStamper? = null
        try {
            stamper = PdfStamper(reader, FileOutputStream(output))
            stamper.setEncryption(
                password.toByteArray(Charsets.UTF_8),
                java.util.UUID.randomUUID().toString().toByteArray(Charsets.UTF_8),
                PdfWriter.ALLOW_PRINTING,
                PdfWriter.ENCRYPTION_AES_128
            )
        } finally {
            runCatching { stamper?.close() }
            reader.close()
        }
        saveToPublicStorage(context, output, output.name)
        output.absolutePath
    }

    /**
     * Compatibility optimizer. It is now bounded to one page bitmap at a time, preventing the old
     * memory spikes. Phase 4 will replace the rasterizing algorithm because it can remove text,
     * links, vectors, forms and annotations.
     */
    fun optimizePdf(
        context: Context,
        pdfPath: String,
        quality: Int = 60
    ): Result<String> = runCatching {
        val safeQuality = quality.coerceIn(20, 95)
        val source = File(pdfPath)
        require(source.isFile) { "PDF file does not exist" }
        val output = createOutputFile(
            context,
            "${sanitize(source.nameWithoutExtension)}_optimized_${timestamp()}.pdf"
        )

        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }

        val document = Document()
        try {
            val writer = PdfWriter.getInstance(document, FileOutputStream(output))
            writer.setFullCompression()
            document.open()

            for (pageIndex in 0 until renderer.pageCount) {
                renderer.openPage(pageIndex).use { page ->
                    val targetDimension = when {
                        safeQuality <= 45 -> 1100
                        safeQuality <= 70 -> 1500
                        else -> 1900
                    }
                    val bitmap = renderBoundedPage(
                        page,
                        targetDimension,
                        Bitmap.Config.RGB_565
                    )
                    try {
                        val bytes = ByteArrayOutputStream().use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, safeQuality, stream)
                            stream.toByteArray()
                        }
                        val image = Image.getInstance(bytes)
                        val pageRect = Rectangle(image.scaledWidth, image.scaledHeight)
                        document.setPageSize(pageRect)
                        document.newPage()
                        image.setAbsolutePosition(0f, 0f)
                        document.add(image)
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
        } finally {
            if (document.isOpen) document.close()
            renderer.close()
            descriptor.close()
        }

        saveToPublicStorage(context, output, output.name)
        output.absolutePath
    }

    private fun copyPages(reader: PdfReader, pages: List<Int>, output: File) {
        val document = Document()
        try {
            val copy = PdfCopy(document, FileOutputStream(output))
            document.open()
            pages.forEach { pageNumber ->
                copy.addPage(copy.getImportedPage(reader, pageNumber))
            }
        } finally {
            if (document.isOpen) document.close()
        }
    }

    private fun renderPageForWidth(
        page: PdfRenderer.Page,
        width: Int,
        config: Bitmap.Config
    ): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val ratio = safeWidth.toFloat() / page.width.toFloat().coerceAtLeast(1f)
        val height = (page.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(safeWidth, height, config).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    private fun renderBoundedPage(
        page: PdfRenderer.Page,
        maxDimension: Int,
        config: Bitmap.Config
    ): Bitmap {
        val largest = maxOf(page.width, page.height).coerceAtLeast(1)
        val scale = if (largest > maxDimension) {
            maxDimension.toFloat() / largest.toFloat()
        } else {
            1f
        }
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, config).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        }
    }

    private fun saveImage(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$APP_FOLDER"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                } ?: return false
                true
            } else {
                // Phase 1 intentionally removed broad WRITE_EXTERNAL_STORAGE. Use the app-specific
                // external Pictures directory on Android 8/9; Phase 4 can add an explicit SAF export.
                val baseDirectory =
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val directory = File(baseDirectory, APP_FOLDER)
                directory.mkdirs()
                val output = File(directory, fileName)
                FileOutputStream(output).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
                }
                true
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to save extracted image", error)
            false
        }
    }

    private fun saveToPublicStorage(context: Context, sourceFile: File, fileName: String) {
        val storageLocation = SettingsPreferences.getStorageLocation(context)
        if (storageLocation == StorageLocation.INTERNAL) return

        // With Phase-1's permission cleanup, direct public-folder writes are intentionally disabled
        // on Android 8/9. The internal file remains authoritative and shareable via FileProvider.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(TAG, "Skipping legacy public PDF copy; SAF export will replace it in Phase 4")
            return
        }

        runCatching {
            val relativePath = when (storageLocation) {
                StorageLocation.DOCUMENTS -> "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER"
                StorageLocation.DOWNLOADS -> "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER"
                StorageLocation.INTERNAL -> return
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            ) ?: return@runCatching
            context.contentResolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            }
        }.onFailure { error ->
            Log.e(TAG, "Unable to make optional public PDF copy", error)
        }
    }

    private fun createOutputFile(context: Context, fileName: String): File {
        val directory = File(context.filesDir, "documents")
        require(directory.exists() || directory.mkdirs()) { "Unable to create documents directory" }
        return File(directory, fileName)
    }

    private fun sanitize(name: String): String =
        name.removeSuffix(".pdf")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .take(80)
            .ifBlank { "document" }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
