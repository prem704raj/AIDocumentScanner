package com.example.aidocumentscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * OCR Engine using ML Kit for offline text recognition.
 * Provides text extraction and keyword search functionality.
 */
object OcrEngine {
    
    private const val TAG = "OcrEngine"
    
    data class TextBlock(
        val text: String,
        val boundingBox: RectF?,
        val lineIndex: Int,
        val wordIndex: Int
    )
    
    data class OcrResult(
        val fullText: String,
        val blocks: List<TextBlock>,
        val confidence: Float
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
        val context: String,  // Surrounding text for preview
        val matchedText: String
    )
    
    /**
     * Model download status
     */
    enum class ModelStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }
    
    private var textRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    
    /**
     * Initialize OCR engine
     */
    fun initialize(context: Context) {
        if (textRecognizer == null) {
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }
    
    /**
     * Check if OCR model is ready for use
     */
    fun isModelReady(): Boolean = textRecognizer != null
    
    /**
     * Force download OCR model (ML Kit downloads automatically, but we provide this for UI feedback)
     */
    fun downloadModel(context: Context, onComplete: (Boolean) -> Unit) {
        initialize(context)
        // ML Kit downloads the model automatically on first process() call.
        // We attempt a lightweight call to trigger the download.
        try {
            val dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val image = InputImage.fromBitmap(dummyBitmap, 0)
            textRecognizer?.process(image)
                ?.addOnSuccessListener { onComplete(true) }
                ?.addOnFailureListener { onComplete(false) }
        } catch (e: Exception) {
            onComplete(false)
        }
    }

    /**
     * Extract text from a single bitmap image
     */
    suspend fun extractText(bitmap: Bitmap): OcrResult {
        if (!isModelReady()) {
            Log.w(TAG, "OCR model not ready, initializing...")
            // Try to initialize
            // Note: In real app, you'd wait for model to be ready
        }
        
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                textRecognizer?.process(image)
                    ?.addOnSuccessListener { visionText ->
                        val blocks = mutableListOf<TextBlock>()
                        var lineIndex = 0
                        
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                var wordIndex = 0
                                for (element in line.elements) {
                                    val boundingBox = element.boundingBox?.let {
                                        RectF(it.left.toFloat(), it.top.toFloat(), 
                                              it.right.toFloat(), it.bottom.toFloat())
                                    }
                                    blocks.add(TextBlock(
                                        text = element.text,
                                        boundingBox = boundingBox,
                                        lineIndex = lineIndex,
                                        wordIndex = wordIndex
                                    ))
                                    wordIndex++
                                }
                                lineIndex++
                            }
                        }
                        
                        continuation.resume(OcrResult(
                            fullText = visionText.text,
                            blocks = blocks,
                            confidence = 0.9f  // ML Kit doesn't provide confidence
                        ))
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "OCR failed: ${e.message}", e)
                        continuation.resume(OcrResult("", emptyList(), 0f))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "OCR exception: ${e.message}", e)
                continuation.resume(OcrResult("", emptyList(), 0f))
            }
        }
    }
    
    /**
     * Extract text from all pages of a PDF
     */
    suspend fun extractTextFromPdf(context: Context, pdfPath: String): List<PageOcrResult> {
        val results = mutableListOf<PageOcrResult>()
        
        try {
            val file = File(pdfPath)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Render at good quality for OCR
                val scale = 2f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                
                val ocrResult = extractText(bitmap)
                results.add(PageOcrResult(i, ocrResult))
                
                bitmap.recycle()
            }
            
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            Log.e(TAG, "PDF OCR failed: ${e.message}", e)
        }
        
        return results
    }
    
    /**
     * Search for keyword in extracted text
     * Returns matches with surrounding context
     */
    fun searchKeyword(
        pagesText: List<PageOcrResult>,
        keyword: String,
        caseSensitive: Boolean = false
    ): List<SearchMatch> {
        val matches = mutableListOf<SearchMatch>()
        val searchKeyword = if (caseSensitive) keyword else keyword.lowercase()
        
        for (pageResult in pagesText) {
            val text = if (caseSensitive) pageResult.result.fullText 
                       else pageResult.result.fullText.lowercase()
            val originalText = pageResult.result.fullText
            
            var index = 0
            while (true) {
                val matchIndex = text.indexOf(searchKeyword, index)
                if (matchIndex == -1) break
                
                // Calculate line index
                val textUpToMatch = originalText.substring(0, matchIndex)
                val lineIndex = textUpToMatch.count { it == '\n' }
                
                // Get context (50 chars before and after)
                val contextStart = maxOf(0, matchIndex - 50)
                val contextEnd = minOf(originalText.length, matchIndex + keyword.length + 50)
                val context = originalText.substring(contextStart, contextEnd)
                    .replace("\n", " ")
                    .trim()
                
                matches.add(SearchMatch(
                    pageIndex = pageResult.pageIndex,
                    lineIndex = lineIndex,
                    startOffset = matchIndex,
                    endOffset = matchIndex + keyword.length,
                    context = "...$context...",
                    matchedText = originalText.substring(matchIndex, matchIndex + keyword.length)
                ))
                
                index = matchIndex + 1
            }
        }
        
        return matches
    }
    
    /**
     * Get combined text from all pages
     */
    fun getCombinedText(pagesText: List<PageOcrResult>): String {
        return pagesText.mapIndexed { index, result ->
            "--- Page ${index + 1} ---\n\n${result.result.fullText}"
        }.joinToString("\n\n")
    }
    
    /**
     * Count total words across all pages
     */
    fun countWords(pagesText: List<PageOcrResult>): Int {
        return pagesText.sumOf { 
            it.result.fullText.split(Regex("\\s+")).filter { word -> word.isNotBlank() }.size 
        }
    }
}
