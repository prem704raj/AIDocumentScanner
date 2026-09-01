package com.example.aidocumentscanner.ocr

/**
 * Versioned, length-prefixed representation for OCR text stored in Document.extractedText.
 *
 * It preserves page boundaries without adding a Room migration. Length-prefixing is used instead
 * of a delimiter so arbitrary OCR text/newlines cannot corrupt the stored format.
 */
object OcrTextCodec {
    private const val HEADER = "DOCUSCAN_OCR_V1\n"

    data class PageText(
        val pageIndex: Int,
        val text: String
    )

    fun encode(pages: List<PageText>): String {
        val builder = StringBuilder(HEADER)
        pages.sortedBy { it.pageIndex }.forEach { page ->
            builder.append(page.pageIndex)
                .append(':')
                .append(page.text.length)
                .append('\n')
                .append(page.text)
        }
        return builder.toString()
    }

    fun decode(value: String?): List<PageText> {
        if (value.isNullOrEmpty()) return emptyList()

        // Backward compatibility with any pre-Phase-5 combined text.
        if (!value.startsWith(HEADER)) {
            return listOf(PageText(0, value))
        }

        val pages = mutableListOf<PageText>()
        var cursor = HEADER.length

        while (cursor < value.length) {
            val lineEnd = value.indexOf('\n', cursor)
            if (lineEnd < 0) break

            val meta = value.substring(cursor, lineEnd)
            val colon = meta.indexOf(':')
            if (colon <= 0) break

            val pageIndex = meta.substring(0, colon).toIntOrNull() ?: break
            val length = meta.substring(colon + 1).toIntOrNull() ?: break
            val textStart = lineEnd + 1
            val textEnd = textStart + length

            if (length < 0 || textEnd > value.length) break
            pages += PageText(pageIndex, value.substring(textStart, textEnd))
            cursor = textEnd
        }

        return pages
    }

    fun combinedHumanReadable(pages: List<PageText>): String =
        pages.sortedBy { it.pageIndex }.joinToString("\n\n") { page ->
            "Page ${page.pageIndex + 1}\n${page.text}"
        }

    fun wordCount(pages: List<PageText>): Int =
        pages.sumOf { page ->
            page.text.split(Regex("\\s+")).count(String::isNotBlank)
        }
}
