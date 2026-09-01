package com.example.aidocumentscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Bundled, offline ML Kit OCR engine.
 *
 * Phase 5 changes:
 * - recognizer can never be "half initialized"
 * - PDF rendering is bounded
 * - page progress is exposed
 * - page boundaries are persisted through OcrTextCodec
 * - no fake confidence value
 */
object OcrEngine {
    private const val TAG = "OcrEngine"
    private const val MAX_OCR_PIXELS = 4_000_000L

    data class TextBlock(
        val text: String,
        val boundingBox: RectF?,
        val lineIndex: Int,
        val wordIndex: Int
    )

    data class OcrResult(
        val fullText: String,
        val blocks: List<TextBlock>
    )

    data class PageOcrResult(
        val pageIndex: Int,
        val result: OcrResult
    )

    data class SearchMatch(
        val pageIndex: Int,
        val lineIndex: Int,
        val startOffset: Int,
        val endOffset: Int,
        val context: String,
        val matchedText: String
    )

    private val recognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun initialize(context: Context) {
        // Bundled Latin recognizer requires no model-download UI.
        recognizer
    }

    fun isModelReady(): Boolean = true

    fun downloadModel(context: Context, onComplete: (Boolean) -> Unit) {
        initialize(context)
        onComplete(true)
    }

    suspend fun extractText(bitmap: Bitmap): OcrResult {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        val image = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            val task = recognizer.process(image)
            task.addOnSuccessListener { visionText ->
                if (!continuation.isActive) return@addOnSuccessListener

                val blocks = mutableListOf<TextBlock>()
                var lineIndex = 0
                visionText.textBlocks.forEach { block ->
                    block.lines.forEach { line ->
                        line.elements.forEachIndexed { wordIndex, element ->
                            val box = element.boundingBox?.let {
                                RectF(
                                    it.left.toFloat(),
                                    it.top.toFloat(),
                                    it.right.toFloat(),
                                    it.bottom.toFloat()
                                )
                            }
                            blocks += TextBlock(
                                text = element.text,
                                boundingBox = box,
                                lineIndex = lineIndex,
                                wordIndex = wordIndex
                            )
                        }
                        lineIndex++
                    }
                }

                continuation.resume(
                    OcrResult(
                        fullText = visionText.text,
                        blocks = blocks
                    )
                )
            }
            task.addOnFailureListener { error ->
                Log.e(TAG, "OCR failed", error)
                if (continuation.isActive) {
                    continuation.resume(OcrResult("", emptyList()))
                }
            }
        }
    }

    suspend fun extractTextFromPdf(
        context: Context,
        pdfPath: String,
        onProgress: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): List<PageOcrResult> {
        initialize(context)
        val file = File(pdfPath)
        require(file.isFile) { "PDF does not exist" }

        val descriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }

        try {
            val results = ArrayList<PageOcrResult>(renderer.pageCount)

            for (pageIndex in 0 until renderer.pageCount) {
                coroutineContext.ensureActive()
                onProgress(pageIndex + 1, renderer.pageCount)

                renderer.openPage(pageIndex).use { page ->
                    val originalPixels = page.width.toLong() * page.height.toLong()
                    val scale = if (originalPixels > MAX_OCR_PIXELS) {
                        sqrt(MAX_OCR_PIXELS.toDouble() / originalPixels.toDouble())
                    } else {
                        1.0
                    }

                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )
                        results += PageOcrResult(
                            pageIndex = pageIndex,
                            result = extractText(bitmap)
                        )
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }

            return results
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    fun encodeForPersistence(pages: List<PageOcrResult>): String =
        OcrTextCodec.encode(
            pages.map { page ->
                OcrTextCodec.PageText(page.pageIndex, page.result.fullText)
            }
        )

    fun decodePersisted(value: String?): List<PageOcrResult> =
        OcrTextCodec.decode(value).map { page ->
            PageOcrResult(
                pageIndex = page.pageIndex,
                result = OcrResult(page.text, emptyList())
            )
        }

    fun searchKeyword(
        pagesText: List<PageOcrResult>,
        keyword: String,
        caseSensitive: Boolean = false
    ): List<SearchMatch> {
        if (keyword.isBlank()) return emptyList()

        val matches = mutableListOf<SearchMatch>()
        val needle = if (caseSensitive) keyword else keyword.lowercase()

        pagesText.forEach { page ->
            val original = page.result.fullText
            val searchable = if (caseSensitive) original else original.lowercase()

            var cursor = 0
            while (cursor <= searchable.length - needle.length) {
                val index = searchable.indexOf(needle, cursor)
                if (index < 0) break

                val start = (index - 60).coerceAtLeast(0)
                val end = (index + keyword.length + 80).coerceAtMost(original.length)
                val line = original.substring(0, index).count { it == '\n' }

                matches += SearchMatch(
                    pageIndex = page.pageIndex,
                    lineIndex = line,
                    startOffset = index,
                    endOffset = index + keyword.length,
                    context = original.substring(start, end)
                        .replace('\n', ' ')
                        .trim(),
                    matchedText = original.substring(
                        index,
                        (index + keyword.length).coerceAtMost(original.length)
                    )
                )

                cursor = index + needle.length.coerceAtLeast(1)
            }
        }

        return matches
    }

    fun getCombinedText(pagesText: List<PageOcrResult>): String =
        OcrTextCodec.combinedHumanReadable(
            pagesText.map {
                OcrTextCodec.PageText(it.pageIndex, it.result.fullText)
            }
        )

    fun countWords(pagesText: List<PageOcrResult>): Int =
        OcrTextCodec.wordCount(
            pagesText.map {
                OcrTextCodec.PageText(it.pageIndex, it.result.fullText)
            }
        )
}
