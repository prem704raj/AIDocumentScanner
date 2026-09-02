package com.example.aidocumentscanner.storage

import com.example.aidocumentscanner.data.Document
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DocumentFileStoreTest {

    @Test
    fun delete_removesPdfAndThumbnail() {
        val dir =
            Files.createTempDirectory(
                "docuscan_file_test"
            ).toFile()

        try {
            val pdf =
                dir.resolve("a.pdf")
                    .apply {
                        writeText("pdf")
                    }

            val thumb =
                dir.resolve("a.jpg")
                    .apply {
                        writeText("thumb")
                    }

            val document =
                Document(
                    id = 1,
                    name = "A",
                    pdfPath =
                        pdf.absolutePath,
                    thumbnailPath =
                        thumb.absolutePath,
                    pageCount = 1
                )

            val store =
                DocumentFileStore()

            assertTrue(
                store
                    .deleteDocumentFiles(
                        document
                    )
                    .isSuccess
            )
            assertFalse(pdf.exists())
            assertFalse(thumb.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun delete_missingFiles_isIdempotent() {
        val dir =
            Files.createTempDirectory(
                "docuscan_missing_test"
            ).toFile()

        try {
            val document =
                Document(
                    id = 2,
                    name = "Missing",
                    pdfPath =
                        dir.resolve(
                            "missing.pdf"
                        ).absolutePath,
                    thumbnailPath = null,
                    pageCount = 1
                )

            assertTrue(
                DocumentFileStore()
                    .deleteDocumentFiles(
                        document
                    )
                    .isSuccess
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
