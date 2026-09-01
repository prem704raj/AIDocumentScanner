package com.example.aidocumentscanner.pdf

import android.content.Context
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import java.io.File
import java.util.UUID

/**
 * Creates a fresh Room record and fresh thumbnail for every derivative PDF.
 * Never reuses the source document's thumbnail path.
 */
object PdfDocumentRegistrar {

    suspend fun register(
        context: Context,
        repository: DocumentRepository,
        pdfPath: String,
        displayName: String = File(pdfPath).nameWithoutExtension
    ): Long {
        val file = File(pdfPath)
        require(file.isFile && file.length() > 0L) { "Output PDF was not created" }

        val pageCount = PdfEditor.getPageCount(pdfPath)
        require(pageCount > 0) { "Output PDF has no readable pages" }

        var thumbnailPath: String? = null
        PdfEditor.renderPageToBitmap(
            context = context,
            pdfPath = pdfPath,
            pageIndex = 0,
            maxWidth = 360
        )?.let { bitmap ->
            try {
                thumbnailPath = PdfGenerator.generateThumbnail(
                    context,
                    bitmap,
                    UUID.randomUUID().toString()
                )
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

        return repository.insertDocument(
            Document(
                name = displayName.removeSuffix(".pdf"),
                pdfPath = file.absolutePath,
                thumbnailPath = thumbnailPath,
                pageCount = pageCount,
                size = file.length()
            )
        )
    }
}
