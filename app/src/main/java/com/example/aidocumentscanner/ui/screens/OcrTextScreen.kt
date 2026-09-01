package com.example.aidocumentscanner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTextScreen(
    documentId: Long,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var document by remember { mutableStateOf<Document?>(null) }
    var pages by remember {
        mutableStateOf<List<OcrEngine.PageOcrResult>>(emptyList())
    }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }

    suspend fun loadOrIndex(force: Boolean) {
        processing = true
        try {
            val doc = withContext(Dispatchers.IO) {
                repository.getDocumentById(documentId)
            } ?: error("Document not found")

            document = doc

            if (!force && doc.isOcrProcessed) {
                pages = OcrEngine.decodePersisted(doc.extractedText)
            } else {
                val extracted = withContext(Dispatchers.IO) {
                    OcrEngine.extractTextFromPdf(
                        context,
                        doc.pdfPath
                    ) { current, total ->
                        progress = "Page $current of $total"
                    }
                }

                withContext(Dispatchers.IO) {
                    repository.updateOcrText(
                        doc.id,
                        OcrEngine.encodeForPersistence(extracted)
                    )
                }
                pages = extracted
                document = withContext(Dispatchers.IO) {
                    repository.getDocumentById(documentId)
                }
            }
        } finally {
            processing = false
            progress = ""
        }
    }

    LaunchedEffect(documentId) {
        runCatching { loadOrIndex(false) }
            .onFailure {
                snackbar.showSnackbar(it.message ?: "OCR failed")
            }
    }

    val readableText = OcrEngine.getCombinedText(pages)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            document?.name ?: "Extracted text",
                            fontWeight = FontWeight.Bold
                        )
                        if (pages.isNotEmpty()) {
                            Text(
                                "${pages.size} pages • ${OcrEngine.countWords(pages)} words",
                                style = MaterialTheme.typography.bodySmall
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
                        enabled = pages.isNotEmpty() && !processing,
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    document?.name ?: "OCR text",
                                    readableText
                                )
                            )
                            scope.launch { snackbar.showSnackbar("Text copied") }
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                    }

                    IconButton(
                        enabled = pages.isNotEmpty() && !processing,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, readableText)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share extracted text")
                            )
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share text")
                    }

                    IconButton(
                        enabled = !processing,
                        onClick = {
                            scope.launch {
                                runCatching { loadOrIndex(true) }
                                    .onSuccess {
                                        snackbar.showSnackbar("OCR refreshed")
                                    }
                                    .onFailure {
                                        snackbar.showSnackbar(
                                            it.message ?: "OCR refresh failed"
                                        )
                                    }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-run OCR")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                processing -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(if (progress.isBlank()) "Reading document…" else progress)
                        Text(
                            "OCR runs on this device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                pages.isEmpty() -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No readable text found.")
                        Spacer(Modifier.size(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { loadOrIndex(true) }
                                }
                            }
                        ) {
                            Text("Try OCR again")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            pages,
                            key = { it.pageIndex }
                        ) { page ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Page ${page.pageIndex + 1}",
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                onOpenPage(page.pageIndex)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.OpenInNew,
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open")
                                        }
                                    }

                                    Text(
                                        page.result.fullText.ifBlank {
                                            "No text detected on this page."
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
