package com.example.aidocumentscanner.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CrashReporterInstrumentedTest {

    private val context =
        ApplicationProvider
            .getApplicationContext<
                android.content.Context
            >()

    @Test
    fun clearAllCrashes_removesOnlyCrashTxtFiles() {
        val dir =
            File(
                context.filesDir,
                "crashes"
            )
        dir.mkdirs()

        File(
            dir,
            "crash_test.txt"
        ).writeText(
            "test"
        )

        CrashReporter
            .clearAllCrashes(
                context
            )

        assertEquals(
            0,
            CrashReporter
                .getPendingCrashes(
                    context
                )
                .size
        )

        assertTrue(
            dir.exists()
        )
    }
}
