package com.example.aidocumentscanner.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextCodecTest {

    @Test
    fun encodeDecode_preservesPageBoundariesAndArbitraryText() {
        val pages =
            listOf(
                OcrTextCodec.PageText(
                    0,
                    "DBMS\n0:5\nhello"
                ),
                OcrTextCodec.PageText(
                    4,
                    "Unicode: नमस्ते ✓"
                )
            )

        val encoded =
            OcrTextCodec.encode(pages)

        val decoded =
            OcrTextCodec.decode(encoded)

        assertEquals(pages, decoded)
    }

    @Test
    fun decode_legacyTextBecomesPageZero() {
        val decoded =
            OcrTextCodec.decode(
                "old OCR content"
            )

        assertEquals(1, decoded.size)
        assertEquals(0, decoded.single().pageIndex)
        assertEquals(
            "old OCR content",
            decoded.single().text
        )
    }

    @Test
    fun wordCount_handlesWhitespace() {
        val count =
            OcrTextCodec.wordCount(
                listOf(
                    OcrTextCodec.PageText(
                        0,
                        "one   two\nthree"
                    )
                )
            )

        assertEquals(3, count)
    }

    @Test
    fun truncatedVersionedPayload_doesNotCrash() {
        val decoded =
            OcrTextCodec.decode(
                "DOCUSCAN_OCR_V1\n0:99\nshort"
            )

        assertTrue(decoded.isEmpty())
    }
}
