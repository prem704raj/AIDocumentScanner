package com.example.aidocumentscanner.privacy

import android.content.Context
import android.net.Uri

/**
 * Tracks public MediaStore copies created by DocuScan from Phase 8 onward.
 * Only content:// URIs are stored; no OCR text or PDF content is duplicated here.
 */
object ExportRegistry {
    private const val PREFS_NAME = "export_registry"
    private const val KEY_PUBLIC_URIS = "public_copy_uris"

    data class DeleteResult(
        val deletedOrMissing: Int,
        val failed: Int
    )

    fun recordPublicCopy(context: Context, uri: Uri) {
        if (uri.scheme != "content") return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PUBLIC_URIS, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        current += uri.toString()
        prefs.edit().putStringSet(KEY_PUBLIC_URIS, current.toSet()).apply()
    }

    fun trackedUris(context: Context): List<Uri> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_PUBLIC_URIS, emptySet())
            .orEmpty()
            .mapNotNull { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
            .filter { it.scheme == "content" }

    fun trackedCount(context: Context): Int = trackedUris(context).size

    fun deleteTrackedPublicCopies(context: Context): DeleteResult {
        var deletedOrMissing = 0
        var failed = 0

        trackedUris(context).forEach { uri ->
            try {
                // 0 can simply mean the user already deleted/moved the item.
                context.contentResolver.delete(uri, null, null)
                deletedOrMissing++
            } catch (_: Throwable) {
                failed++
            }
        }

        clear(context)
        return DeleteResult(deletedOrMissing, failed)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
