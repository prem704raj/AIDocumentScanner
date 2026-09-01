package com.example.aidocumentscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.pdf.PdfDocumentRegistrar
import com.example.aidocumentscanner.pdf.PdfEditor
import com.example.aidocumentscanner.pdf.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOptimizerScreen(
    initialDocumentId: Long? = null,
    onBack: () -> Unit,
    onOptimized: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val documents by repository.getAllDocuments().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var selected by remember { mutableStateOf<Document?>(null) }
    var processing by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<PdfEditor.OptimizationResult?>(null) }

    LaunchedEffect(initialDocumentId, documents) {
        if (selected == null && initialDocumentId != null && initialDocumentId > 0L) {
            selected = documents.firstOrNull { it.id == initialDocumentId }
                ?: withContext(Dispatchers.IO) {
                    repository.getDocumentById(initialDocumentId)
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Safe PDF Optimizer", fontWeight = FontWeight.Bold)
                        Text(
                            "Preserves document structure",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selected != null && initialDocumentId == null) {
                                selected = null
                                lastResult = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (selected == null) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Icon(Icons.Default.Compress, contentDescription = null)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "What this optimizer does",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "It removes unused PDF objects and enables full PDF " +
                                        "compression. It does not intentionally rasterize pages, " +
                                        "so searchable text, links, vectors, forms and annotations " +
                                        "are preserved as far as the PDF library can preserve them."
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Some already-compressed PDFs may become 0% smaller. " +
                                        "DocuScan will not create a larger optimized copy.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    items(documents, key = { it.id }) { document ->
                        Surface(
                            onClick = {
                                selected = document
                                lastResult = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    document.name,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${document.pageCount} pages • " +
                                        PdfGenerator.formatFileSize(document.size),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                val document = selected!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(document.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${document.pageCount} pages • " +
                                    PdfGenerator.formatFileSize(document.size)
                            )
                        }
                    }

                    Text(
                        "No guessed reduction percentage is shown. The app measures the " +
                            "real output after optimization.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    lastResult?.let { result ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Actual result",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${PdfGenerator.formatFileSize(result.originalBytes)} → " +
                                        PdfGenerator.formatFileSize(result.optimizedBytes)
                                )
                                Text(
                                    String.format(
                                        java.util.Locale.US,
                                        "%.1f%% smaller",
                                        result.reductionPercent
                                    )
                                )
                            }
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !processing,
                        onClick = {
                            scope.launch {
                                processing = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        PdfEditor.optimizePdfStructure(
                                            context,
                                            document.pdfPath
                                        ).getOrThrow()
                                    }
                                    lastResult = result

                                    val id = withContext(Dispatchers.IO) {
                                        PdfDocumentRegistrar.register(
                                            context,
                                            repository,
                                            result.outputPath,
                                            "${document.name}_optimized"
                                        )
                                    }
                                    onOptimized(id)
                                } catch (error: Throwable) {
                                    snackbar.showSnackbar(
                                        error.message ?: "Optimization failed"
                                    )
                                } finally {
                                    processing = false
                                }
                            }
                        }
                    ) {
                        Text("Optimize safely")
                    }
                }
            }

            if (processing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Optimizing locally…")
                    }
                }
            }
        }
    }
}
