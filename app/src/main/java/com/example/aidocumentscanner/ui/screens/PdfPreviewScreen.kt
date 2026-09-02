package com.example.aidocumentscanner.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.pdf.PdfGenerator
import com.example.aidocumentscanner.scanner.StudentModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    pages: List<Bitmap>,
    onSave: (Long) -> Unit,
    onAddMore: () -> Unit,
    onBack: () -> Unit,
    onReorderPages: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DocumentRepository(context) }
    val studentSettings by StudentModeManager.getSettings(context)
        .collectAsState(
            initial = StudentModeManager.StudentModeSettings()
        )

    var documentName by remember { mutableStateOf("Scan") }
    var nameEditedManually by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    var pageSize by remember {
        mutableStateOf(PdfGenerator.PageSizeType.A4)
    }
    var quality by remember {
        mutableStateOf(PdfGenerator.QualityType.HIGH)
    }

    LaunchedEffect(studentSettings) {
        if (studentSettings.enabled) {
            pageSize = when (studentSettings.preset.recommendedPageSize) {
                "FIT_IMAGE" -> PdfGenerator.PageSizeType.FIT_IMAGE
                "LETTER" -> PdfGenerator.PageSizeType.LETTER
                "LEGAL" -> PdfGenerator.PageSizeType.LEGAL
                else -> PdfGenerator.PageSizeType.A4
            }
            quality = when (studentSettings.preset.recommendedQuality) {
                "STANDARD" -> PdfGenerator.QualityType.STANDARD
                "ULTRA" -> PdfGenerator.QualityType.ULTRA
                else -> PdfGenerator.QualityType.HIGH
            }

            if (studentSettings.autoFilename && !nameEditedManually) {
                documentName = StudentModeManager.generateFilename(
                    studentSettings
                )
            }
        } else if (!nameEditedManually && documentName == "Scan") {
            documentName = "Scan_${System.currentTimeMillis() / 1000}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Create PDF", fontWeight = FontWeight.Bold)
                        Text(
                            "${pages.size} page${if (pages.size == 1) "" else "s"}",
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
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "PDF settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { showSettings = true },
                            label = { Text(pageSize.name) }
                        )
                        AssistChip(
                            onClick = { showSettings = true },
                            label = { Text(quality.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }

                    if (studentSettings.enabled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        studentSettings.preset.label,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        studentSettings.selectedSubjectName.ifBlank {
                                            "No subject selected"
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = pages.isNotEmpty() && !saving,
                        onClick = { showNameDialog = true }
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…")
                        } else {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Save PDF")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = onAddMore) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add pages")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(pages) { index, bitmap ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(
                                        bitmap.width.toFloat() /
                                            bitmap.height.toFloat()
                                    ),
                                contentScale = ContentScale.Fit
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Page ${index + 1}",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(
                                    enabled = index > 0,
                                    onClick = {
                                        onReorderPages(index, index - 1)
                                    }
                                ) { Text("Up") }
                                TextButton(
                                    enabled = index < pages.lastIndex,
                                    onClick = {
                                        onReorderPages(index, index + 1)
                                    }
                                ) { Text("Down") }
                            }
                        }
                    }
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onAddMore
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add more pages")
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name PDF") },
            text = {
                Column {
                    OutlinedTextField(
                        value = documentName,
                        onValueChange = {
                            documentName = it.take(100)
                            nameEditedManually = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Document name") },
                        singleLine = true
                    )
                    if (studentSettings.enabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Study metadata: ${studentSettings.preset.label}" +
                                if (studentSettings.selectedSubjectName.isNotBlank()) {
                                    " • ${studentSettings.selectedSubjectName}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = documentName.isNotBlank() && !saving,
                    onClick = {
                        showNameDialog = false
                        saving = true

                        scope.launch {
                            try {
                                val safeName = documentName.trim()
                                val pdfPath = withContext(Dispatchers.IO) {
                                    PdfGenerator.generatePdf(
                                        context = context,
                                        images = pages,
                                        fileName = safeName,
                                        pageSize = pageSize,
                                        quality = quality
                                    )
                                }

                                val thumbnailPath = withContext(Dispatchers.IO) {
                                    pages.firstOrNull()?.let { firstPage ->
                                        PdfGenerator.generateThumbnail(
                                            context,
                                            firstPage,
                                            System.currentTimeMillis().toString()
                                        )
                                    }
                                }

                                val document = Document(
                                    name = safeName,
                                    pdfPath = pdfPath,
                                    thumbnailPath = thumbnailPath,
                                    pageCount = pages.size,
                                    size = PdfGenerator.getFileSize(pdfPath),
                                    folderId = if (studentSettings.enabled) {
                                        studentSettings.selectedSubjectId
                                    } else {
                                        null
                                    },
                                    documentType = StudentModeManager.documentType(
                                        studentSettings
                                    )
                                )

                                val id = withContext(Dispatchers.IO) {
                                    repository.insertDocument(document).also {
                                        document.folderId?.let { folderId ->
                                            repository.updateFolderCount(folderId)
                                        }
                                    }
                                }

                                Toast.makeText(
                                    context,
                                    "PDF saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSave(id)
                            } catch (error: Throwable) {
                                Toast.makeText(
                                    context,
                                    error.message ?: "Could not save PDF",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNameDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "PDF settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Page size",
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfGenerator.PageSizeType.entries.forEach { option ->
                        FilterChip(
                            selected = pageSize == option,
                            onClick = { pageSize = option },
                            label = { Text(option.name) }
                        )
                    }
                }

                Text(
                    "Quality",
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfGenerator.QualityType.entries.forEach { option ->
                        Card(
                            onClick = { quality = option },
                            colors = CardDefaults.cardColors(
                                containerColor = if (quality == option) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.label)
                                    Text(
                                        "${option.maxDimension}px maximum image dimension",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (quality == option) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected"
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showSettings = false }
                ) {
                    Text("Done")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
