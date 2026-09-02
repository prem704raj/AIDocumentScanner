package com.example.aidocumentscanner.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aidocumentscanner.DocuScanApplication
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.pdf.PdfGenerator
import com.example.aidocumentscanner.ui.documents.DocumentSort
import com.example.aidocumentscanner.ui.documents.DocumentsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onDocumentClick: (Long) -> Unit,
    onBack: () -> Unit,
    onSearchTextClick: () -> Unit = {},
    onOcrTextClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as DocuScanApplication
    val viewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModel.Factory(
            repository = app.container.documentRepository,
            fileStore = app.container.documentFileStore
        )
    )

    val documents by viewModel.allDocuments.collectAsState()
    val visible by viewModel.visibleDocuments.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    var menuDocument by remember { mutableStateOf<Document?>(null) }
    var renameDocument by remember { mutableStateOf<Document?>(null) }
    var deleteDocument by remember { mutableStateOf<Document?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Documents", fontWeight = FontWeight.Bold)
                        Text(
                            "${documents.size} document${if (documents.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                },
                actions = {
                    IconButton(
                        onClick = onSearchTextClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.FindInPage,
                            contentDescription = "Search inside documents"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Filter by document name") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortButton(
                    text = "Recent",
                    selected = uiState.sort == DocumentSort.RECENT,
                    onClick = { viewModel.setSort(DocumentSort.RECENT) }
                )
                SortButton(
                    text = "Name",
                    selected = uiState.sort == DocumentSort.NAME,
                    onClick = { viewModel.setSort(DocumentSort.NAME) }
                )
                SortButton(
                    text = "Pages",
                    selected = uiState.sort == DocumentSort.PAGES,
                    onClick = { viewModel.setSort(DocumentSort.PAGES) }
                )
            }

            if (visible.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (documents.isEmpty()) {
                                "No documents yet"
                            } else {
                                "No matching documents"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (documents.isEmpty()) {
                                "Scan or import pages from Home to create your first PDF."
                            } else {
                                "Try another document name or clear the filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { document ->
                        DocumentListCard(
                            document = document,
                            onOpen = { onDocumentClick(document.id) },
                            onMore = { menuDocument = document }
                        )
                    }
                }
            }
        }
    }

    menuDocument?.let { document ->
        ModalBottomSheet(
            onDismissRequest = { menuDocument = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Text(
                document.name.removeSuffix(".pdf"),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            ListItem(
                headlineContent = { Text("Open") },
                leadingContent = {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                supportingContent = null
            )
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = {
                    menuDocument = null
                    onDocumentClick(document.id)
                }
            ) { Text("Open document") }

            HorizontalDivider()

            BottomAction(
                icon = Icons.Default.FindInPage,
                title = "Extracted text",
                subtitle = if (document.isOcrProcessed) {
                    "View saved OCR text"
                } else {
                    "Run offline OCR and view text"
                },
                onClick = {
                    menuDocument = null
                    onOcrTextClick(document.id)
                }
            )
            BottomAction(
                icon = Icons.Default.Share,
                title = "Share",
                subtitle = "Send the PDF with Android share sheet",
                onClick = {
                    menuDocument = null
                    shareDocumentPhase9(context, viewModel, document)
                }
            )
            BottomAction(
                icon = Icons.Default.Edit,
                title = "Rename",
                subtitle = "Change the name shown in DocuScan",
                onClick = {
                    menuDocument = null
                    renameDocument = document
                }
            )
            BottomAction(
                icon = Icons.Default.Delete,
                title = "Delete",
                subtitle = "Remove the local PDF and its thumbnail",
                destructive = true,
                onClick = {
                    menuDocument = null
                    deleteDocument = document
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    renameDocument?.let { document ->
        var value by remember(document.id) {
            mutableStateOf(document.name.removeSuffix(".pdf"))
        }

        AlertDialog(
            onDismissRequest = { renameDocument = null },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = value.isNotBlank(),
                    onClick = {
                        val newName = value.trim()
                        viewModel.renameDocument(document.id, newName)
                        renameDocument = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDocument = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteDocument?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteDocument = null },
            title = { Text("Delete document?") },
            text = {
                Text(
                    "\"${document.name.removeSuffix(".pdf")}\" will be permanently removed from this device."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        viewModel.deleteDocument(document)
                        deleteDocument = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDocument = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SortButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
    )
}

@Composable
private fun DocumentListCard(
    document: Document,
    onOpen: () -> Unit,
    onMore: () -> Unit
) {
    Card(
        onClick = onOpen,
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
            Box(
                modifier = Modifier.size(width = 60.dp, height = 76.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!document.thumbnailPath.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(document.thumbnailPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Preview of ${document.name}",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        document.name.removeSuffix(".pdf"),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    document.emoji?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(it)
                    }
                }
                Text(
                    "${document.pageCount} page${if (document.pageCount == 1) "" else "s"} • " +
                        PdfGenerator.formatFileSize(document.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Updated ${formatDocumentDate(document.updatedAt)}" +
                        if (document.isOcrProcessed) " • Text indexed" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onMore,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options for ${document.name}"
                )
            }
        }
    }
}

@Composable
private fun BottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    )
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(title)
    }
}

private fun shareDocumentPhase9(
    context: Context,
    viewModel: DocumentsViewModel,
    document: Document
) {
    val file = File(document.pdfPath)
    if (!file.isFile) {
        Toast.makeText(context, "PDF file is missing", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
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
        viewModel.markShared(document.id)
    }.onFailure {
        Toast.makeText(context, "Could not share PDF", Toast.LENGTH_SHORT).show()
    }
}

private fun formatDocumentDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        .format(Date(timestamp))
