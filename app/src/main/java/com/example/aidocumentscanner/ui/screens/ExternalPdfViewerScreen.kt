package com.example.aidocumentscanner.ui.screens

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.ui.components.InAppPdfViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ExternalPdfViewerScreen(
    pdfUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DocumentRepository(context) }

    var pdfName by remember(pdfUri) { mutableStateOf("PDF Document.pdf") }
    var pageCount by remember(pdfUri) { mutableIntStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    pdfUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            pdfName = cursor.getString(index) ?: pdfName
                        }
                    }
                }

                context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Throwable) {
                // Viewer will display its own error state if the URI cannot be read.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            pdfName.removeSuffix(".pdf"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                "$pageCount pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share PDF"))
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Failed to share PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    IconButton(
                        enabled = !isImporting,
                        onClick = {
                            scope.launch {
                                isImporting = true
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val input = context.contentResolver.openInputStream(pdfUri)
                                            ?: error("Could not open selected PDF")

                                        val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
                                        val safeBase = pdfName
                                            .removeSuffix(".pdf")
                                            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                            .take(80)
                                            .ifBlank { "Imported_PDF" }
                                        val stamp = SimpleDateFormat(
                                            "yyyyMMdd_HHmmss_SSS",
                                            Locale.US
                                        ).format(Date())
                                        val output = File(documentsDir, "${safeBase}_$stamp.pdf")

                                        input.use { source ->
                                            FileOutputStream(output).use { target ->
                                                source.copyTo(target)
                                            }
                                        }

                                        val importedPageCount = runCatching {
                                            android.os.ParcelFileDescriptor.open(
                                                output,
                                                android.os.ParcelFileDescriptor.MODE_READ_ONLY
                                            ).use { pfd ->
                                                PdfRenderer(pfd).use { it.pageCount }
                                            }
                                        }.getOrDefault(pageCount)

                                        val id = repository.insertDocument(
                                            Document(
                                                name = output.nameWithoutExtension,
                                                pdfPath = output.absolutePath,
                                                thumbnailPath = null,
                                                pageCount = importedPageCount,
                                                size = output.length()
                                            )
                                        )

                                        id
                                    }
                                }

                                isImporting = false
                                result.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "PDF imported to My Documents",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "Import failed: ${it.message ?: "unknown error"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator()
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Import")
                        }
                    }
                }
            )
        }
    ) { padding ->
        InAppPdfViewer(
            pdfUri = pdfUri,
            modifier = Modifier.padding(padding)
        )
    }
}
