package com.example.aidocumentscanner.scanner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.studentModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "student_mode"
)

/**
 * Student scanning is deliberately configuration, not a second document store.
 * Subject ownership lives in Room Folder rows; this object stores only the active workflow.
 */
object StudentModeManager {
    private val ENABLED = booleanPreferencesKey("student_mode_enabled")
    private val AUTO_ENHANCE = booleanPreferencesKey("student_auto_enhance")
    private val AUTO_FILENAME = booleanPreferencesKey("auto_filename")
    private val SUBJECT_ID = longPreferencesKey("student_subject_id")

    // Reuse the original key so an older stored subject string survives Phase 7.
    private val SUBJECT_NAME = stringPreferencesKey("subject")
    private val PRESET = stringPreferencesKey("student_scan_preset")

    enum class StudentScanPreset(
        val storageValue: String,
        val label: String,
        val shortLabel: String,
        val description: String,
        val recommendedFilter: ImageEnhancer.FilterType,
        val recommendedPageSize: String,
        val recommendedQuality: String
    ) {
        NOTES(
            storageValue = "notes",
            label = "Notes",
            shortLabel = "Notes",
            description = "Notebook pages, handwritten notes and printed notes.",
            recommendedFilter = ImageEnhancer.FilterType.DOCUMENT,
            recommendedPageSize = "A4",
            recommendedQuality = "HIGH"
        ),
        ASSIGNMENT(
            storageValue = "assignment",
            label = "Assignment",
            shortLabel = "Assignment",
            description = "Submitted work, lab sheets and written assignments.",
            recommendedFilter = ImageEnhancer.FilterType.DOCUMENT,
            recommendedPageSize = "A4",
            recommendedQuality = "HIGH"
        ),
        LECTURE(
            storageValue = "lecture",
            label = "Lecture notes",
            shortLabel = "Lecture",
            description = "Fast multi-page classroom and lecture scanning.",
            recommendedFilter = ImageEnhancer.FilterType.DOCUMENT,
            recommendedPageSize = "A4",
            recommendedQuality = "HIGH"
        ),
        WHITEBOARD(
            storageValue = "whiteboard",
            label = "Whiteboard",
            shortLabel = "Whiteboard",
            description = "Preserve colored diagrams and board writing.",
            recommendedFilter = ImageEnhancer.FilterType.COLOR_ENHANCE,
            recommendedPageSize = "FIT_IMAGE",
            recommendedQuality = "HIGH"
        ),
        BOOK(
            storageValue = "book",
            label = "Book pages",
            shortLabel = "Book",
            description = "Textbook, reference-book and chapter pages.",
            recommendedFilter = ImageEnhancer.FilterType.DOCUMENT,
            recommendedPageSize = "FIT_IMAGE",
            recommendedQuality = "HIGH"
        ),
        QUESTION_PAPER(
            storageValue = "question_paper",
            label = "Question paper",
            shortLabel = "Question Paper",
            description = "Exam papers, sample papers and worksheets.",
            recommendedFilter = ImageEnhancer.FilterType.DOCUMENT,
            recommendedPageSize = "A4",
            recommendedQuality = "HIGH"
        );

        companion object {
            fun fromStored(value: String?): StudentScanPreset =
                entries.firstOrNull {
                    it.storageValue == value || it.name == value
                } ?: NOTES
        }
    }

    data class StudentModeSettings(
        val enabled: Boolean = false,
        val autoEnhance: Boolean = true,
        val autoFilename: Boolean = true,
        val selectedSubjectId: Long? = null,
        val selectedSubjectName: String = "",
        val preset: StudentScanPreset = StudentScanPreset.NOTES
    )

    fun getSettings(context: Context): Flow<StudentModeSettings> =
        context.studentModeDataStore.data.map { prefs ->
            StudentModeSettings(
                enabled = prefs[ENABLED] ?: false,
                autoEnhance = prefs[AUTO_ENHANCE] ?: true,
                autoFilename = prefs[AUTO_FILENAME] ?: true,
                selectedSubjectId = prefs[SUBJECT_ID]?.takeIf { it > 0L },
                selectedSubjectName = prefs[SUBJECT_NAME].orEmpty(),
                preset = StudentScanPreset.fromStored(prefs[PRESET])
            )
        }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.studentModeDataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setAutoEnhance(context: Context, enabled: Boolean) {
        context.studentModeDataStore.edit { it[AUTO_ENHANCE] = enabled }
    }

    suspend fun setAutoFilename(context: Context, enabled: Boolean) {
        context.studentModeDataStore.edit { it[AUTO_FILENAME] = enabled }
    }

    suspend fun setPreset(
        context: Context,
        preset: StudentScanPreset
    ) {
        context.studentModeDataStore.edit {
            it[PRESET] = preset.storageValue
        }
    }

    suspend fun selectSubject(
        context: Context,
        folderId: Long?,
        folderName: String
    ) {
        context.studentModeDataStore.edit { prefs ->
            if (folderId == null || folderId <= 0L) {
                prefs.remove(SUBJECT_ID)
                prefs[SUBJECT_NAME] = ""
            } else {
                prefs[SUBJECT_ID] = folderId
                prefs[SUBJECT_NAME] = folderName.trim()
            }
        }
    }

    suspend fun reset(context: Context) {
        context.studentModeDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun configureQuickScan(
        context: Context,
        preset: StudentScanPreset,
        subjectId: Long?,
        subjectName: String
    ) {
        context.studentModeDataStore.edit { prefs ->
            prefs[ENABLED] = true
            prefs[PRESET] = preset.storageValue
            if (subjectId != null && subjectId > 0L) {
                prefs[SUBJECT_ID] = subjectId
                prefs[SUBJECT_NAME] = subjectName.trim()
            }
        }
    }

    fun documentType(settings: StudentModeSettings): String? =
        if (settings.enabled) {
            "student:${settings.preset.storageValue}"
        } else {
            null
        }

    fun generateFilename(
        settings: StudentModeSettings,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val date = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date(timestamp))

        val subject = sanitizeToken(
            settings.selectedSubjectName.ifBlank { "Study" }
        )
        val preset = sanitizeToken(settings.preset.shortLabel)

        return "${subject}_${preset}_$date"
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(90)
            .ifBlank { "Study_Scan_$date" }
    }

    private fun sanitizeToken(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "")
            .take(40)

    val suggestedSubjects: List<String> = listOf(
        "Mathematics",
        "Physics",
        "Chemistry",
        "Computer Science",
        "DSA",
        "DBMS",
        "OOP",
        "COA",
        "Operating Systems",
        "Computer Networks",
        "English",
        "Aptitude"
    )
}
