package com.example.aidocumentscanner.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.ui.components.InAppPdfViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.material.icons.filled.TextFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: Long,
    initialPage: Int = 0,
    onBack: () -> Unit,
    onOcrClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    var document by remember(documentId) { mutableStateOf<Document?>(null) }
    var loading by remember(documentId) { mutableStateOf(true) }

    LaunchedEffect(documentId) {
        loading = true
        document = withContext(Dispatchers.IO) {
            repository.getDocumentById(documentId)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document?.name?.removeSuffix(".pdf") ?: "Document",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        document?.let { doc ->
                            Text(
                                text = "${doc.pageCount} pages",
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
                    document?.let { doc ->
                        IconButton(onClick = { onOcrClick(doc.id) }) {
                            Icon(Icons.Default.TextFields, contentDescription = "Extract text (OCR)")
                        }
                        IconButton(onClick = { shareDocument(context, doc) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { openWithExternalApp(context, doc) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open with another app")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            document == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Document not found")
                }
            }

            else -> {
                val file = File(document!!.pdfPath)
                InAppPdfViewer(
                    pdfUri = Uri.fromFile(file),
                    initialPage = initialPage,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

private fun shareDocument(context: Context, document: Document) {
    runCatching {
        val file = File(document.pdfPath)
        require(file.isFile) { "PDF file is missing" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }.onFailure {
        Toast.makeText(context, "Failed to share PDF", Toast.LENGTH_SHORT).show()
    }
}

private fun openWithExternalApp(context: Context, document: Document) {
    runCatching {
        val file = File(document.pdfPath)
        require(file.isFile) { "PDF file is missing" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }.onFailure {
        Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
    }
}
