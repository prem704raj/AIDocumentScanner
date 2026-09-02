package com.example.aidocumentscanner.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.pdf.PdfGenerator
import com.example.aidocumentscanner.privacy.PrivacyDataManager
import com.example.aidocumentscanner.util.CrashReporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onDataCleared: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var snapshot by remember { mutableStateOf<PrivacyDataManager.Snapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var erasing by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var includePublicCopies by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        snapshot = runCatching { PrivacyDataManager.snapshot(context) }.getOrNull()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Privacy & data", fontWeight = FontWeight.Bold)
                        Text(
                            "What DocuScan accesses, stores and deletes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !loading && !erasing,
                        onClick = { scope.launch { refresh() } },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh privacy summary")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading && snapshot == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { TrustCard() }
                    item { SectionTitle("Access & processing", "Permissions and data paths used by this build.") }
                    item {
                        PrivacyFact(
                            Icons.Default.CameraAlt,
                            "Camera",
                            if (snapshot?.cameraPermissionGranted == true) "Permission granted" else "Permission not granted",
                            "Camera is optional and is used when you choose to scan. Images can also be selected with Android's system Photo Picker."
                        )
                    }
                    item {
                        PrivacyFact(
                            Icons.Default.WifiOff,
                            "Internet permission",
                            "Not requested",
                            "The production manifest does not request INTERNET. Core scanning, PDF operations and bundled OCR are designed to run on-device."
                        )
                    }
                    item {
                        PrivacyFact(
                            Icons.Default.Backup,
                            "Android app backup",
                            "Disabled",
                            "Manifest backup is disabled and app files, databases and preferences are excluded from backup/device-transfer rules."
                        )
                    }
                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            }
                        ) { Text("Open Android app permissions") }
                    }

                    item { SectionTitle("Stored by DocuScan", "Current app-managed data on this device.") }
                    snapshot?.let { data -> item { LocalDataSummary(data) } }
                    item {
                        PrivacyFact(
                            Icons.Default.Description,
                            "OCR text",
                            "${snapshot?.indexedOcrDocuments ?: 0} indexed document(s)",
                            "Extracted OCR text is stored in the local Room database so Search can reuse it after restart."
                        )
                    }
                    item {
                        PrivacyFact(
                            Icons.Default.Folder,
                            "Study Mode",
                            "${snapshot?.subjectCount ?: 0} subject(s)",
                            "Subjects are local Folder rows. Active Study Mode subject/preset settings are stored in local DataStore preferences."
                        )
                    }
                    item {
                        PrivacyFact(
                            Icons.Default.PrivacyTip,
                            "Crash reports",
                            "${snapshot?.crashReportCount ?: 0} local report(s)",
                            "Up to 10 technical crash reports may be stored locally for debugging. They are not uploaded automatically."
                        )
                    }
                    if ((snapshot?.crashReportCount ?: 0) > 0) {
                        item {
                            FilledTonalButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    CrashReporter.clearAllCrashes(context)
                                    scope.launch {
                                        snackbar.showSnackbar("Local crash reports cleared")
                                        refresh()
                                    }
                                }
                            ) { Text("Clear local crash reports") }
                        }
                    }

                    item { SectionTitle("Sharing & public copies", "A privacy boundary changes when you intentionally export or share.") }
                    item {
                        PrivacyFact(
                            Icons.Default.Share,
                            "Android sharing",
                            "Only when you choose Share",
                            "DocuScan grants the app you select access to a content URI. That recipient can save its own copy, which DocuScan cannot control or erase later."
                        )
                    }
                    item {
                        PrivacyFact(
                            Icons.Default.Storage,
                            "Public Documents / Downloads copies",
                            "${snapshot?.trackedPublicCopies ?: 0} tracked future copy/copies",
                            "From Phase 8 onward, DocuScan records MediaStore URIs for public copies it creates. Older copies created before tracking may remain untracked."
                        )
                    }

                    item { SectionTitle("In-app privacy notice", "Plain-language behavior of this build.") }
                    item { PrivacyNotice() }

                    item { SectionTitle("Erase data", "Destructive and cannot be undone.") }
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Erase DocuScan document data",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    "Deletes document records, OCR text, subjects, internal PDFs, thumbnails, tool outputs, cache, local crash reports and Study Mode data. Appearance/theme is preserved.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !erasing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = {
                                        includePublicCopies = (snapshot?.trackedPublicCopies ?: 0) > 0
                                        showEraseDialog = true
                                    }
                                ) { Text("Erase document data") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }

            if (erasing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Erasing local data…")
                    }
                }
            }
        }
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { if (!erasing) showEraseDialog = false },
            title = { Text("Erase DocuScan data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This removes DocuScan's local PDFs, OCR text, subjects, metadata, tool outputs, cache and crash reports.")
                    if ((snapshot?.trackedPublicCopies ?: 0) > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = includePublicCopies,
                                onCheckedChange = { includePublicCopies = it }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Also delete tracked public copies", fontWeight = FontWeight.Medium)
                                Text(
                                    "${snapshot?.trackedPublicCopies ?: 0} tracked public copy/copies. Older copies or recipient-app copies may still remain.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Text(
                        "This action cannot be undone.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showEraseDialog = false
                        erasing = true
                        scope.launch {
                            val result = PrivacyDataManager.eraseAllManagedData(
                                context,
                                includePublicCopies
                            )
                            erasing = false
                            if (result.publicCopiesFailed > 0) {
                                snackbar.showSnackbar(
                                    "${result.publicCopiesFailed} public copy/copies could not be deleted"
                                )
                            }
                            if (result.errors.isNotEmpty()) {
                                snackbar.showSnackbar(result.errors.first())
                                refresh()
                            } else {
                                onDataCleared()
                            }
                        }
                    }
                ) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrustCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Local by default", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "DocuScan is designed so core scanning, PDF tools and bundled OCR work without a developer server or account. Exported/shared copies can leave app-private storage when you explicitly choose that action."
            )
        }
    }
}

@Composable
private fun LocalDataSummary(data: PrivacyDataManager.Snapshot) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DataRow("Documents", data.documentCount.toString())
            DataRow("Study documents", data.studyDocumentCount.toString())
            DataRow("Internal PDFs", PdfGenerator.formatFileSize(data.internalPdfBytes))
            DataRow("Thumbnails", PdfGenerator.formatFileSize(data.thumbnailBytes))
            DataRow("PDF tool outputs", PdfGenerator.formatFileSize(data.toolOutputBytes))
            DataRow("Database", PdfGenerator.formatFileSize(data.databaseBytes))
            DataRow("Temporary cache", PdfGenerator.formatFileSize(data.temporaryCacheBytes))
            DataRow("Crash logs", PdfGenerator.formatFileSize(data.crashLogBytes))
            androidx.compose.material3.HorizontalDivider()
            DataRow("Approx. app-managed total", PdfGenerator.formatFileSize(data.totalAppManagedBytes), true)
        }
    }
}

@Composable
private fun DataRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun PrivacyFact(icon: ImageVector, title: String, value: String, detail: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyNotice() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Notice("Access", "Camera is accessed only after Android permission is granted. User-selected images/PDFs are accessed through Android system pickers/content URIs.")
            Notice("Processing", "Document correction, image filters, PDF operations and bundled OCR are designed to run locally in the app.")
            Notice("Storage", "DocuScan stores PDFs, thumbnails, metadata, OCR text, Study Mode organization and limited technical crash logs locally.")
            Notice("Sharing", "When you choose Share or a public save location, the selected content can leave app-private storage.")
            Notice("Accounts", "Core features do not require a DocuScan account.")
            Notice("Deletion", "Use the erase control here to remove app-managed document data. Historical public or recipient-app copies may need separate deletion.")
        }
    }
}

@Composable
private fun Notice(title: String, body: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
