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
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.sqrt

object OcrEngine {

    private const val TAG = "OcrEngine"
    private const val MAX_RENDER_PIXELS = 4_000_000L
    const val PAGE_SEPARATOR = "\u000C"

    data class TextBlock(
        val text: String,
        val boundingBox: RectF?,
        val lineIndex: Int,
        val wordIndex: Int
    )

    data class OcrResult(
        val fullText: String,
        val blocks: List<TextBlock>,
        /** ML Kit text-recognition does not expose a document-level confidence value. */
        val confidence: Float = 0f
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

    enum class ModelStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }

    private val recognizer: TextRecognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun initialize(context: Context) {
        // The currently used com.google.mlkit:text-recognition artifact is bundled.
        // Touch the lazy recognizer so failures happen during initialization rather than mid-search.
        recognizer
    }

    fun isModelReady(): Boolean = true

    fun downloadModel(context: Context, onComplete: (Boolean) -> Unit) {
        return try {
            initialize(context)
            onComplete(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize OCR", t)
            onComplete(false)
        }
    }

    suspend fun extractText(bitmap: Bitmap): OcrResult {
        if (bitmap.isRecycled) return OcrResult("", emptyList())

        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val task = recognizer.process(image)

                task.addOnSuccessListener { visionText ->
                    if (!continuation.isActive) return@addOnSuccessListener

                    val blocks = mutableListOf<TextBlock>()
                    var lineIndex = 0

                    visionText.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.elements.forEachIndexed { wordIndex, element ->
                                val rect = element.boundingBox?.let {
                                    RectF(
                                        it.left.toFloat(),
                                        it.top.toFloat(),
                                        it.right.toFloat(),
                                        it.bottom.toFloat()
                                    )
                                }
                                blocks += TextBlock(
                                    text = element.text,
                                    boundingBox = rect,
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
                            blocks = blocks,
                            confidence = 0f
                        )
                    )
                }

                task.addOnFailureListener { error ->
                    Log.e(TAG, "OCR failed", error)
                    if (continuation.isActive) {
                        continuation.resume(OcrResult("", emptyList()))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "OCR exception", t)
                if (continuation.isActive) {
                    continuation.resume(OcrResult("", emptyList()))
                }
            }
        }
    }

    suspend fun extractTextFromPdf(
        context: Context,
        pdfPath: String
    ): List<PageOcrResult> = withContext(Dispatchers.IO) {
        val file = File(pdfPath)
        if (!file.exists() || !file.isFile) return@withContext emptyList()

        val results = mutableListOf<PageOcrResult>()

        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val sourcePixels = page.width.toLong() * page.height.toLong()
                            val scale = if (sourcePixels <= 0L) {
                                1f
                            } else {
                                sqrt(MAX_RENDER_PIXELS.toDouble() / sourcePixels.toDouble())
                                    .toFloat()
                                    .coerceAtMost(2f)
                                    .coerceAtLeast(0.25f)
                            }

                            val width = max(1, (page.width * scale).toInt())
                            val height = max(1, (page.height * scale).toInt())
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(
                                    bitmap,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                )
                                results += PageOcrResult(index, extractText(bitmap))
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PDF OCR failed", t)
        }

        results
    }

    fun searchKeyword(
        pagesText: List<PageOcrResult>,
        keyword: String,
        caseSensitive: Boolean = false
    ): List<SearchMatch> {
        if (keyword.isBlank()) return emptyList()

        val matches = mutableListOf<SearchMatch>()
        val needle = if (caseSensitive) keyword else keyword.lowercase()

        pagesText.forEach { pageResult ->
            val original = pageResult.result.fullText
            val haystack = if (caseSensitive) original else original.lowercase()
            var searchFrom = 0

            while (searchFrom <= haystack.length - needle.length) {
                val matchIndex = haystack.indexOf(needle, searchFrom)
                if (matchIndex < 0) break

                val contextStart = (matchIndex - 50).coerceAtLeast(0)
                val contextEnd = (matchIndex + keyword.length + 50).coerceAtMost(original.length)
                val context = original.substring(contextStart, contextEnd)
                    .replace('\n', ' ')
                    .trim()

                matches += SearchMatch(
                    pageIndex = pageResult.pageIndex,
                    lineIndex = original.substring(0, matchIndex).count { it == '\n' },
                    startOffset = matchIndex,
                    endOffset = matchIndex + keyword.length,
                    context = context,
                    matchedText = original.substring(matchIndex, matchIndex + keyword.length)
                )

                searchFrom = matchIndex + max(1, needle.length)
            }
        }

        return matches
    }

    fun getCombinedText(pagesText: List<PageOcrResult>): String =
        pagesText.joinToString(PAGE_SEPARATOR) { it.result.fullText }

    fun splitCombinedText(combinedText: String?): List<String> =
        combinedText
            ?.split(PAGE_SEPARATOR)
            ?.map { it.trim() }
            ?: emptyList()

    fun countWords(pagesText: List<PageOcrResult>): Int =
        pagesText.sumOf { page ->
            page.result.fullText
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
        }
}
