package com.example.aidocumentscanner

import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.scanner.StudentModeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Phase7UnitTest {

    @Test
    fun studentScanPreset_fromStoredAndProperties() {
        val notes = StudentModeManager.StudentScanPreset.fromStored("notes")
        assertEquals(StudentModeManager.StudentScanPreset.NOTES, notes)
        assertEquals(ImageEnhancer.FilterType.DOCUMENT, notes.recommendedFilter)
        assertEquals("A4", notes.recommendedPageSize)
        assertEquals("HIGH", notes.recommendedQuality)

        val whiteboard = StudentModeManager.StudentScanPreset.fromStored("whiteboard")
        assertEquals(StudentModeManager.StudentScanPreset.WHITEBOARD, whiteboard)
        assertEquals(ImageEnhancer.FilterType.COLOR_ENHANCE, whiteboard.recommendedFilter)
        assertEquals("FIT_IMAGE", whiteboard.recommendedPageSize)
        assertEquals("HIGH", whiteboard.recommendedQuality)

        val unknown = StudentModeManager.StudentScanPreset.fromStored("invalid_preset_xyz")
        assertEquals(StudentModeManager.StudentScanPreset.NOTES, unknown)

        val questionPaper = StudentModeManager.StudentScanPreset.fromStored("question_paper")
        assertEquals(StudentModeManager.StudentScanPreset.QUESTION_PAPER, questionPaper)
        assertEquals(ImageEnhancer.FilterType.DOCUMENT, questionPaper.recommendedFilter)
    }

    @Test
    fun generateFilename_formattingAndSanitization() {
        val timestamp = 1756704000000L // Specific epoch
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

        // Normal subject + preset
        val settings1 = StudentModeManager.StudentModeSettings(
            enabled = true,
            selectedSubjectName = "DBMS",
            preset = StudentModeManager.StudentScanPreset.LECTURE
        )
        val filename1 = StudentModeManager.generateFilename(settings1, timestamp)
        assertEquals("DBMS_Lecture_$dateStr", filename1)

        // Empty subject -> Study fallback
        val settings2 = StudentModeManager.StudentModeSettings(
            enabled = true,
            selectedSubjectName = "",
            preset = StudentModeManager.StudentScanPreset.NOTES
        )
        val filename2 = StudentModeManager.generateFilename(settings2, timestamp)
        assertEquals("Study_Notes_$dateStr", filename2)

        // Special characters and multiple spaces
        val settings3 = StudentModeManager.StudentModeSettings(
            enabled = true,
            selectedSubjectName = "Data Structures & Algorithms (Lab #1)",
            preset = StudentModeManager.StudentScanPreset.ASSIGNMENT
        )
        val filename3 = StudentModeManager.generateFilename(settings3, timestamp)
        assertEquals("Data_Structures_Algorithms_Lab_1_Assignment_$dateStr", filename3)

        // Long subject name truncation
        val settingsLong = StudentModeManager.StudentModeSettings(
            enabled = true,
            selectedSubjectName = "A".repeat(100),
            preset = StudentModeManager.StudentScanPreset.NOTES
        )
        val filenameLong = StudentModeManager.generateFilename(settingsLong, timestamp)
        assertTrue(filenameLong.length <= 90)
    }

    @Test
    fun documentType_enabledAndDisabled() {
        val disabledSettings = StudentModeManager.StudentModeSettings(
            enabled = false,
            preset = StudentModeManager.StudentScanPreset.NOTES
        )
        assertNull(StudentModeManager.documentType(disabledSettings))

        val enabledNotes = StudentModeManager.StudentModeSettings(
            enabled = true,
            preset = StudentModeManager.StudentScanPreset.NOTES
        )
        assertEquals("student:notes", StudentModeManager.documentType(enabledNotes))

        val enabledWhiteboard = StudentModeManager.StudentModeSettings(
            enabled = true,
            preset = StudentModeManager.StudentScanPreset.WHITEBOARD
        )
        assertEquals("student:whiteboard", StudentModeManager.documentType(enabledWhiteboard))

        val enabledBook = StudentModeManager.StudentModeSettings(
            enabled = true,
            preset = StudentModeManager.StudentScanPreset.BOOK
        )
        assertEquals("student:book", StudentModeManager.documentType(enabledBook))
    }

    @Test
    fun suggestedSubjects_nonEmptyAndContainsStandardCourses() {
        val list = StudentModeManager.suggestedSubjects
        assertTrue(list.isNotEmpty())
        assertTrue(list.contains("DBMS"))
        assertTrue(list.contains("DSA"))
        assertTrue(list.contains("Mathematics"))
        assertTrue(list.contains("Operating Systems"))
    }
}
