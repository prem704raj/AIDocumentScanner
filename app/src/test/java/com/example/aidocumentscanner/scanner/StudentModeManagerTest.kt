package com.example.aidocumentscanner.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class StudentModeManagerTest {

    @Test
    fun filename_containsSubjectPresetAndDate() {
        val previous =
            TimeZone.getDefault()

        try {
            TimeZone.setDefault(
                TimeZone.getTimeZone(
                    "UTC"
                )
            )

            val settings =
                StudentModeManager
                    .StudentModeSettings(
                        enabled = true,
                        selectedSubjectName =
                            "DBMS",
                        preset =
                            StudentModeManager
                                .StudentScanPreset
                                .LECTURE
                    )

            assertEquals(
                "DBMS_Lecture_1970-01-01",
                StudentModeManager
                    .generateFilename(
                        settings,
                        timestamp = 0L
                    )
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun filename_sanitizesPunctuationButKeepsUnicodeLetters() {
        val settings =
            StudentModeManager
                .StudentModeSettings(
                    enabled = true,
                    selectedSubjectName =
                        "DBMS / डेटा",
                    preset =
                        StudentModeManager
                            .StudentScanPreset
                            .NOTES
                )

        val name =
            StudentModeManager
                .generateFilename(
                    settings,
                    0L
                )

        assertFalse(name.contains("/"))
        assertTrue(name.contains("डेटा"))
        assertTrue(name.length <= 90)
    }

    @Test
    fun documentType_isNullWhenStudyModeOff() {
        assertEquals(
            null,
            StudentModeManager
                .documentType(
                    StudentModeManager
                        .StudentModeSettings(
                            enabled = false
                        )
                )
        )
    }
}
