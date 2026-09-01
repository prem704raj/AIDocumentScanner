package com.example.aidocumentscanner.util

import android.content.Context
import android.os.Build
import android.os.Looper
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 10

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val crashDir = getCrashDir(context)
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashReport(context, thread, throwable)
            } catch (_: Exception) {}

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                Looper.getMainLooper().quit()
                System.exit(1)
            }
        }
    }

    fun getPendingCrashes(context: Context): List<CrashReport> {
        val crashDir = getCrashDir(context)
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                CrashReport(
                    fileName = file.name,
                    timestamp = file.lastModified(),
                    content = file.readText()
                )
            } ?: emptyList()
    }

    fun clearAllCrashes(context: Context) {
        val crashDir = getCrashDir(context)
        if (!crashDir.exists()) return
        crashDir.listFiles()?.filter { it.name.endsWith(".txt") }?.forEach { it.delete() }
    }

    fun deleteCrashReport(context: Context, fileName: String) {
        val file = File(getCrashDir(context), fileName)
        if (file.exists()) file.delete()
    }

    private fun saveCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val crashDir = getCrashDir(context)
        if (!crashDir.exists()) crashDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.txt")

        FileWriter(crashFile).use { writer ->
            writer.write("=== CRASH REPORT ===\n")
            writer.write("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            writer.write("Thread: ${thread.name} (${thread.id})\n")
            writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            writer.write("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})\n")
            writer.write("App: ${context.packageName}\n\n")
            writer.write("=== EXCEPTION ===\n")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()
            writer.write(sw.toString())
        }

        cleanupOldCrashes(context)
    }

    private fun cleanupOldCrashes(context: Context) {
        val crashDir = getCrashDir(context)
        if (!crashDir.exists()) return
        val files = crashDir.listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList() ?: return
        while (files.size > MAX_CRASH_FILES) {
            files.firstOrNull()?.delete()
            files.removeAt(0)
        }
    }

    private fun getCrashDir(context: Context): File {
        return File(context.filesDir, CRASH_DIR)
    }

    data class CrashReport(
        val fileName: String,
        val timestamp: Long,
        val content: String
    )
}
