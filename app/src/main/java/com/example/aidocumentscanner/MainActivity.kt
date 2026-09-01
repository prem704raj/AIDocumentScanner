package com.example.aidocumentscanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.aidocumentscanner.navigation.AppNavigation
import com.example.aidocumentscanner.scanner.OpenCVManager
import com.example.aidocumentscanner.ui.theme.AIDocumentScannerTheme
import com.example.aidocumentscanner.ui.theme.ThemeMode
import com.example.aidocumentscanner.util.BitmapCache
import com.example.aidocumentscanner.util.CrashReporter
import com.example.aidocumentscanner.util.safeRecycle

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_CRASH_CHECK = "last_crash_check"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize crash reporter
        CrashReporter.initialize(this)

        // Initialize OpenCV asynchronously
        OpenCVManager.initializeAsync(this) { success ->
            if (!success) {
                Log.e("AIDocumentScanner", "OpenCV initialization failed - document detection will not work")
            }
        }

        // Check if app was opened with a PDF file
        val pdfUri = handlePdfIntent(intent)

        // Load saved theme preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedThemeMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.valueOf(savedThemeMode)) }
            var externalPdfUri by remember { mutableStateOf(pdfUri) }
            var showCrashDialog by remember { mutableStateOf(false) }
            var crashReports by remember { mutableStateOf(CrashReporter.getPendingCrashes(this@MainActivity)) }

            // Show crash dialog if there were recent crashes
            LaunchedEffect(Unit) {
                val prefs = this@MainActivity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastCheck = prefs.getLong(KEY_LAST_CRASH_CHECK, 0L)
                val now = System.currentTimeMillis()
                if (crashReports.isNotEmpty() && (now - lastCheck) > 60_000) {
                    showCrashDialog = true
                    prefs.edit().putLong(KEY_LAST_CRASH_CHECK, now).apply()
                }
            }

            if (showCrashDialog && crashReports.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showCrashDialog = false },
                    title = { Text("Previous Crash Detected") },
                    text = {
                        Text(
                            "The app crashed on the last launch. Details have been saved for debugging.\n\n" +
                            "Crash count: ${crashReports.size}"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            CrashReporter.clearAllCrashes(this@MainActivity)
                            crashReports = emptyList()
                            showCrashDialog = false
                        }) {
                            Text("Dismiss")
                        }
                    }
                )
            }

            AIDocumentScannerTheme(themeMode = themeMode) {
                val navController = rememberNavController()

                // Shared state for scanned pages
                val pages = remember { mutableStateListOf<Bitmap>() }

                // Safe bitmap removal with recycling
                val removePage = { index: Int ->
                    if (index in pages.indices) {
                        pages[index].safeRecycle()
                        pages.removeAt(index)
                    }
                }
                
                val clearPages = {
                    pages.forEach { it.safeRecycle() }
                    pages.clear()
                }

                AppNavigation(
                    navController = navController,
                    pages = pages,
                    onAddPage = { bitmap -> pages.add(bitmap) },
                    onAddPages = { bitmaps -> pages.addAll(bitmaps) },
                    onClearPages = clearPages,
                    onRemovePage = removePage,
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        // Save theme preference
                        prefs.edit().putString(KEY_THEME_MODE, newMode.name).apply()
                    },
                    externalPdfUri = externalPdfUri,
                    onExternalPdfHandled = { externalPdfUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new PDF intent when app is already running
        handlePdfIntent(intent)?.let { uri ->
            Log.d("AIDocumentScanner", "Received new PDF intent: $uri")
            // Open the PDF with external viewer
            openPdfWithExternalViewer(uri)
        }
    }

    private fun handlePdfIntent(intent: Intent?): Uri? {
        if (intent == null) return null

        val action = intent.action
        val type = intent.type

        Log.d("AIDocumentScanner", "Intent action: $action, type: $type")

        if (action == Intent.ACTION_VIEW && type == "application/pdf") {
            return intent.data
        }

        return null
    }

    private fun openPdfWithExternalViewer(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }
}