package com.example.aidocumentscanner.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.data.Folder
import com.example.aidocumentscanner.pdf.PdfGenerator
import com.example.aidocumentscanner.scanner.StudentModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSubjectScreen(
    folderId: Long,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenOcrText: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()
    val documents by repository.getDocumentsByFolder(folderId)
        .collectAsState(initial = emptyList())

    var subject by remember { mutableStateOf<Folder?>(null) }

    LaunchedEffect(folderId) {
        subject = withContext(Dispatchers.IO) {
            repository.getFolderById(folderId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            subject?.name ?: "Subject",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${documents.size} document${if (documents.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                subject?.name ?: "Study subject",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "New study scans can be saved into this subject automatically.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.size(10.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val folder = subject ?: return@Button
                                scope.launch {
                                    StudentModeManager.selectSubject(
                                        context,
                                        folder.id,
                                        folder.name
                                    )
                                    StudentModeManager.setEnabled(
                                        context,
                                        true
                                    )
                                    onStartScan()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scan for this subject")
                        }
                    }
                }
            }

            if (documents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                "No documents in this subject yet",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                items(documents, key = { it.id }) { document ->
                    Card(
                        onClick = { onOpenDocument(document.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!document.thumbnailPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(
                                        LocalContext.current
                                    )
                                        .data(document.thumbnailPath)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Preview of ${document.name}",
                                    modifier = Modifier.size(
                                        width = 54.dp,
                                        height = 70.dp
                                    ),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    document.name.removeSuffix(".pdf"),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${document.pageCount} page${if (document.pageCount == 1) "" else "s"} • " +
                                        PdfGenerator.formatFileSize(document.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    presetLabel(document.documentType),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onOpenOcrText(document.id)
                                }
                            ) {
                                Icon(
                                    Icons.Default.FindInPage,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Text")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun presetLabel(documentType: String?): String {
    val value = documentType
        ?.removePrefix("student:")
        ?.takeIf { documentType.startsWith("student:") }
        ?: return "Document"

    return StudentModeManager.StudentScanPreset.entries
        .firstOrNull { it.storageValue == value }
        ?.label
        ?: "Study document"
}
