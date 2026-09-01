package com.example.aidocumentscanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.example.aidocumentscanner.util.CrashReporter

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_CRASH_CHECK = "last_crash_check"
    }

    private var incomingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CrashReporter.initialize(this)

        OpenCVManager.initializeAsync(this) { success ->
            if (!success) {
                Log.e("AIDocumentScanner", "OpenCV initialization failed")
            }
        }

        incomingPdfUri = extractPdfUri(intent)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val initialTheme = runCatching {
            ThemeMode.valueOf(savedTheme ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }
            var showCrashDialog by remember { mutableStateOf(false) }
            var crashReports by remember {
                mutableStateOf(CrashReporter.getPendingCrashes(this@MainActivity))
            }

            LaunchedEffect(Unit) {
                val lastCheck = prefs.getLong(KEY_LAST_CRASH_CHECK, 0L)
                val now = System.currentTimeMillis()
                if (crashReports.isNotEmpty() && now - lastCheck > 60_000L) {
                    showCrashDialog = true
                    prefs.edit().putLong(KEY_LAST_CRASH_CHECK, now).apply()
                }
            }

            if (showCrashDialog && crashReports.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showCrashDialog = false },
                    title = { Text("Previous Crash Detected") },
                    text = { Text("Crash details were saved locally for debugging.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                CrashReporter.clearAllCrashes(this@MainActivity)
                                crashReports = emptyList()
                                showCrashDialog = false
                            }
                        ) {
                            Text("Dismiss")
                        }
                    }
                )
            }

            AIDocumentScannerTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val pages = remember { mutableStateListOf<Bitmap>() }

                AppNavigation(
                    navController = navController,
                    pages = pages,
                    onAddPage = { bitmap -> pages.add(bitmap) },
                    onAddPages = { bitmaps -> pages.addAll(bitmaps) },
                    onClearPages = { pages.clear() },
                    onRemovePage = { index ->
                        if (index in pages.indices) pages.removeAt(index)
                    },
                    onReplacePage = { index, bitmap ->
                        if (index in pages.indices) pages[index] = bitmap
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        prefs.edit().putString(KEY_THEME_MODE, newMode.name).apply()
                    },
                    externalPdfUri = incomingPdfUri,
                    onExternalPdfHandled = { incomingPdfUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPdfUri = extractPdfUri(intent)
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val mime = intent.type ?: contentResolver.getType(uri)
        return if (mime == "application/pdf") uri else null
    }
}