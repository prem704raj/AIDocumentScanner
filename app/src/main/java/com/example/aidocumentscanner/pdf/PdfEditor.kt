package com.example.aidocumentscanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfGState
import com.itextpdf.text.pdf.PdfName
import com.itextpdf.text.pdf.PdfNumber
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Phase-4 PDF editor.
 *
 * Design rule: tools that can preserve the source PDF structure must do so. In particular,
 * optimization no longer rasterizes the document into JPEG pages.
 */
object PdfEditor {
    private const val TAG = "PdfEditor"
    private const val MAX_EXPORT_DIMENSION = 2400

    data class OptimizationResult(
        val outputPath: String,
        val originalBytes: Long,
        val optimizedBytes: Long
    ) {
        val savedBytes: Long get() = (originalBytes - optimizedBytes).coerceAtLeast(0L)
        val reductionPercent: Double
            get() = if (originalBytes <= 0L) 0.0
            else savedBytes * 100.0 / originalBytes.toDouble()
    }

    fun mergePdfs(
        context: Context,
        pdfPaths: List<String>,
        outputName: String
    ): Result<String> = runCatching {
        val inputs = pdfPaths.map(::File)
        require(inputs.size >= 2) { "Choose at least two PDFs to merge" }
        require(inputs.all { it.isFile && it.length() > 0L }) {
            "One or more PDFs cannot be read"
        }

        val output = outputFile(
            context,
            "${sanitize(outputName)}_merged_${timestamp()}.pdf"
        )

        val document = Document()
        var copy: PdfCopy? = null
        try {
            copy = PdfCopy(document, FileOutputStream(output))
            document.open()

            inputs.forEach { file ->
                val reader = PdfReader(file.absolutePath)
                try {
                    for (page in 1..reader.numberOfPages) {
                        copy.addPage(copy.getImportedPage(reader, page))
                    }
                    copy.freeReader(reader)
                } finally {
                    reader.close()
                }
            }
        } finally {
            if (document.isOpen) document.close()
        }

        requirePdf(output)
        output.absolutePath
    }

    fun splitPdfGroups(
        context: Context,
        pdfPath: String,
        groups: List<List<Int>>
    ): Result<List<String>> = runCatching {
        require(groups.size >= 2) { "At least two split groups are required" }

        val reader = PdfReader(pdfPath)
        try {
            val total = reader.numberOfPages
            val outputs = mutableListOf<String>()
            groups.forEachIndexed { index, pages ->
                val valid = pages.distinct().filter { it in 1..total }
                require(valid.isNotEmpty()) { "Split group ${index + 1} has no valid pages" }

                val output = outputFile(
                    context,
                    "${sanitize(File(pdfPath).nameWithoutExtension)}_part${index + 1}_${timestamp()}.pdf"
                )
                copyPages(reader, valid, output)
                requirePdf(output)
                outputs += output.absolutePath
            }
            outputs
        } finally {
            reader.close()
        }
    }

    /** Compatibility wrapper retained for any existing caller. */
    fun splitPdf(
        context: Context,
        pdfPath: String,
        pageRanges: List<IntRange>
    ): Result<List<String>> =
        splitPdfGroups(context, pdfPath, pageRanges.map { it.toList() })

    fun splitPdfByPages(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<String> = runCatching {
        val reader = PdfReader(pdfPath)
        try {
            val valid = pagesToExtract.distinct().filter { it in 1..reader.numberOfPages }
            require(valid.isNotEmpty()) { "No valid pages selected" }
            val output = outputFile(
                context,
                "${sanitize(File(pdfPath).nameWithoutExtension)}_pages_${timestamp()}.pdf"
            )
            copyPages(reader, valid, output)
            requirePdf(output)
            output.absolutePath
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
            val remove = pagesToRemove.toSet()
            val keep = (1..reader.numberOfPages).filterNot(remove::contains)
            require(remove.isNotEmpty()) { "No pages selected" }
            require(keep.isNotEmpty()) { "Cannot remove every page" }

            val output = outputFile(
                context,
                "${sanitize(File(pdfPath).nameWithoutExtension)}_pages_removed_${timestamp()}.pdf"
            )
            copyPages(reader, keep, output)
            requirePdf(output)
            output.absolutePath
        } finally {
            reader.close()
        }
    }

    fun reorderPages(
        context: Context,
        pdfPath: String,
        pageOrder: List<Int>
    ): Result<String> = runCatching {
        val reader = PdfReader(pdfPath)
        try {
            val total = reader.numberOfPages
            require(pageOrder.size == total) { "Page order must contain all $total pages" }
            require(pageOrder.toSet().size == total) { "Each page must appear exactly once" }
            require(pageOrder.all { it in 1..total }) { "Page order is out of range" }

            val output = outputFile(
                context,
                "${sanitize(File(pdfPath).nameWithoutExtension)}_reordered_${timestamp()}.pdf"
            )
            copyPages(reader, pageOrder, output)
            requirePdf(output)
            output.absolutePath
        } finally {
            reader.close()
        }
    }

    fun rotatePages(
        context: Context,
        pdfPath: String,
        pages: List<Int>,
        degreesClockwise: Int
    ): Result<String> = runCatching {
        require(degreesClockwise in listOf(90, 180, 270)) {
            "Rotation must be 90, 180 or 270 degrees"
        }

        val reader = PdfReader(pdfPath)
        val output = outputFile(
            context,
            "${sanitize(File(pdfPath).nameWithoutExtension)}_rotated_${timestamp()}.pdf"
        )
        var stamper: PdfStamper? = null
        try {
            val valid = pages.distinct().filter { it in 1..reader.numberOfPages }
            require(valid.isNotEmpty()) { "No valid pages selected" }

            stamper = PdfStamper(reader, FileOutputStream(output))
            valid.forEach { pageNumber ->
                val pageDictionary = reader.getPageN(pageNumber)
                val current = pageDictionary.getAsNumber(PdfName.ROTATE)?.intValue() ?: 0
                val next = ((current + degreesClockwise) % 360 + 360) % 360
                pageDictionary.put(PdfName.ROTATE, PdfNumber(next))
            }
        } finally {
            runCatching { stamper?.close() }
            reader.close()
        }

        requirePdf(output)
        output.absolutePath
    }

    /**
     * Extracts selected pages to app-private JPEG files. The UI shares them through FileProvider,
     * so Android 8/9 also work without WRITE_EXTERNAL_STORAGE.
     */
    fun extractPagesAsImageFiles(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<List<String>> = runCatching {
        val source = File(pdfPath)
        require(source.isFile) { "PDF file does not exist" }

        val exportDir = File(
            context.filesDir,
            "pdf_tools/exports/${sanitize(source.nameWithoutExtension)}_${timestamp()}"
        )
        require(exportDir.mkdirs() || exportDir.isDirectory) {
            "Could not create export folder"
        }

        val descriptor = ParcelFileDescriptor.open(
            source,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }

        try {
            val valid = pagesToExtract
                .distinct()
                .sorted()
                .filter { it in 1..renderer.pageCount }
            require(valid.isNotEmpty()) { "No valid pages selected" }

            valid.map { pageNumber ->
                renderer.openPage(pageNumber - 1).use { page ->
                    val bitmap = renderBoundedPage(page, MAX_EXPORT_DIMENSION)
                    try {
                        val file = File(
                            exportDir,
                            "${sanitize(source.nameWithoutExtension)}_page_$pageNumber.jpg"
                        )
                        FileOutputStream(file).use { output ->
                            require(
                                bitmap.compress(
                                    Bitmap.CompressFormat.JPEG,
                                    92,
                                    output
                                )
                            ) { "Could not encode page $pageNumber" }
                        }
                        file.absolutePath
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    /** Compatibility count API for old code outside the Phase-4 screen. */
    fun extractPagesAsImages(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<Int> =
        extractPagesAsImageFiles(context, pdfPath, pagesToExtract)
            .map { it.size }

    /**
     * Adds a translucent raster watermark only; source PDF pages remain intact.
     * Rendering the watermark into a bitmap lets Android font fallback handle more scripts
     * than Helvetica/WINANSI.
     */
    fun addWatermark(
        context: Context,
        pdfPath: String,
        watermarkText: String
    ): Result<String> = runCatching {
        val text = watermarkText.trim()
        require(text.isNotEmpty()) { "Watermark text is empty" }
        require(text.length <= 80) { "Watermark is too long" }

        val reader = PdfReader(pdfPath)
        val output = outputFile(
            context,
            "${sanitize(File(pdfPath).nameWithoutExtension)}_watermarked_${timestamp()}.pdf"
        )
        val watermarkBytes = createWatermarkPng(text)

        var stamper: PdfStamper? = null
        try {
            stamper = PdfStamper(reader, FileOutputStream(output))

            for (pageNumber in 1..reader.numberOfPages) {
                val pageSize = reader.getPageSizeWithRotation(pageNumber)
                val image = Image.getInstance(watermarkBytes)
                image.setRotationDegrees(35f)

                val maxWidth = pageSize.width * 0.72f
                val maxHeight = pageSize.height * 0.24f
                image.scaleToFit(maxWidth, maxHeight)
                image.setAbsolutePosition(
                    (pageSize.width - image.scaledWidth) / 2f,
                    (pageSize.height - image.scaledHeight) / 2f
                )

                val content = stamper.getOverContent(pageNumber)
                content.saveState()
                try {
                    val state = PdfGState().apply { setFillOpacity(0.30f) }
                    content.setGState(state)
                    content.addImage(image)
                } finally {
                    content.restoreState()
                }
            }
        } finally {
            runCatching { stamper?.close() }
            reader.close()
        }

        requirePdf(output)
        output.absolutePath
    }

    fun passwordProtect(
        context: Context,
        pdfPath: String,
        password: String
    ): Result<String> = runCatching {
        require(password.length >= 6) { "Use at least 6 characters" }

        val reader = PdfReader(pdfPath)
        val output = outputFile(
            context,
            "${sanitize(File(pdfPath).nameWithoutExtension)}_protected_${timestamp()}.pdf"
        )

        val ownerPassword = ByteArray(32).also(SecureRandom()::nextBytes)
        var stamper: PdfStamper? = null
        try {
            stamper = PdfStamper(reader, FileOutputStream(output))
            stamper.setEncryption(
                password.toByteArray(Charsets.UTF_8),
                ownerPassword,
                PdfWriter.ALLOW_PRINTING,
                PdfWriter.ENCRYPTION_AES_128
            )
        } finally {
            runCatching { stamper?.close() }
            reader.close()
        }

        requirePdf(output)
        output.absolutePath
    }

    /**
     * Structure-preserving optimization.
     *
     * It removes unused PDF objects and enables full compression. It intentionally does NOT
     * render pages to bitmaps, so text/searchability, vector graphics, links, form fields and
     * annotations are not deliberately discarded.
     */
    fun optimizePdfStructure(
        context: Context,
        pdfPath: String
    ): Result<OptimizationResult> = runCatching {
        val source = File(pdfPath)
        require(source.isFile && source.length() > 0L) { "PDF file does not exist" }

        val candidate = outputFile(
            context,
            "${sanitize(source.nameWithoutExtension)}_optimized_${timestamp()}.pdf"
        )

        val reader = PdfReader(source.absolutePath)
        var stamper: PdfStamper? = null
        try {
            reader.removeUnusedObjects()
            stamper = PdfStamper(reader, FileOutputStream(candidate))
            stamper.setFullCompression()
        } finally {
            runCatching { stamper?.close() }
            reader.close()
        }

        requirePdf(candidate)

        val finalOutput: File
        if (candidate.length() <= source.length()) {
            finalOutput = candidate
        } else {
            // Never make the user's "optimized" derivative larger.
            runCatching { candidate.delete() }
            finalOutput = outputFile(
                context,
                "${sanitize(source.nameWithoutExtension)}_optimized_copy_${timestamp()}.pdf"
            )
            source.copyTo(finalOutput, overwrite = true)
        }

        OptimizationResult(
            outputPath = finalOutput.absolutePath,
            originalBytes = source.length(),
            optimizedBytes = finalOutput.length()
        )
    }

    /**
     * Legacy signature retained so any caller missed during integration still preserves content.
     * The quality parameter is intentionally ignored; Phase 4 removed rasterizing quality modes.
     */
    fun optimizePdf(
        context: Context,
        pdfPath: String,
        quality: Int = 60
    ): Result<String> =
        optimizePdfStructure(context, pdfPath).map { it.outputPath }

    fun getPageCount(pdfPath: String): Int {
        var reader: PdfReader? = null
        return try {
            reader = PdfReader(pdfPath)
            reader.numberOfPages
        } catch (error: Throwable) {
            Log.e(TAG, "Could not read page count", error)
            0
        } finally {
            reader?.close()
        }
    }

    fun renderPageToBitmap(
        context: Context,
        pdfPath: String,
        pageIndex: Int,
        maxWidth: Int = 1200
    ): Bitmap? {
        val source = File(pdfPath)
        if (!source.isFile) return null

        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        return try {
            descriptor = ParcelFileDescriptor.open(
                source,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            renderer = PdfRenderer(descriptor)
            if (pageIndex !in 0 until renderer.pageCount) return null

            renderer.openPage(pageIndex).use { page ->
                val width = maxWidth.coerceIn(120, 1600)
                val scale = width.toFloat() / page.width.coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.RGB_565
                ).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Could not render page", error)
            null
        } finally {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }

    private fun copyPages(
        reader: PdfReader,
        pages: List<Int>,
        output: File
    ) {
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

    private fun renderBoundedPage(
        page: PdfRenderer.Page,
        maxDimension: Int
    ): Bitmap {
        val largest = max(page.width, page.height).coerceAtLeast(1)
        val scale = if (largest > maxDimension) {
            maxDimension.toFloat() / largest.toFloat()
        } else 1f

        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        ).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
            )
        }
    }

    private fun createWatermarkPng(text: String): ByteArray {
        val width = 1200
        val height = 220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 85, 85, 85)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 92f
        }

        var size = 92f
        while (paint.measureText(text) > width * 0.92f && size > 30f) {
            size -= 4f
            paint.textSize = size
        }

        val y = height / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, width / 2f, y, paint)

        return try {
            ByteArrayOutputStream().use { stream ->
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                stream.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun outputFile(context: Context, name: String): File {
        val dir = File(context.filesDir, "pdf_tools")
        require(dir.exists() || dir.mkdirs()) { "Could not create PDF tools folder" }

        var candidate = File(dir, name)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(
                dir,
                "${candidate.nameWithoutExtension}_$counter.pdf"
            )
            counter++
        }
        return candidate
    }

    private fun requirePdf(file: File) {
        require(file.isFile && file.length() > 0L) { "Output PDF was not created" }
        require(getPageCount(file.absolutePath) > 0) { "Output PDF is unreadable" }
    }

    private fun sanitize(value: String): String =
        value
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .take(80)
            .ifBlank { "Document" }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
