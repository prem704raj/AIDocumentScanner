package com.example.aidocumentscanner.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.pdf.PdfEditor
import com.example.aidocumentscanner.pdf.PdfGenerator
import com.example.aidocumentscanner.ui.theme.GradientEnd
import com.example.aidocumentscanner.ui.theme.GradientMiddle
import com.example.aidocumentscanner.ui.theme.GradientStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Data class to represent external PDF files from file manager
data class ExternalPdfFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val pageCount: Int = 0
)

enum class PdfTool(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val gradient: List<Color>
) {
    MERGE("Merge PDFs", "Combine multiple PDFs", Icons.Default.CallMerge, listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
    SPLIT("Split PDF", "Split into separate PDFs", Icons.Default.CallSplit, listOf(Color(0xFF10B981), Color(0xFF34D399))),
    REMOVE_PAGES("Remove Pages", "Delete specific pages", Icons.Default.Delete, listOf(Color(0xFFEF4444), Color(0xFFF97316))),
    EXTRACT_IMAGES("Extract Images", "Save pages as images", Icons.Default.Image, listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))),
    OPTIMIZE("Optimize PDF", "Reduce file size", Icons.Default.Compress, listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))),
    ADD_WATERMARK("Add Watermark", "Add text watermark", Icons.Default.WaterDrop, listOf(Color(0xFF8B5CF6), Color(0xFFA855F7))),
    PASSWORD_PROTECT("Password Protect", "Secure your PDF", Icons.Default.Lock, listOf(Color(0xFFEC4899), Color(0xFFF472B6)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit,
    onMergeComplete: (Long) -> Unit,
    onOptimizeRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DocumentRepository(context) }
    val documents by repository.getAllDocuments().collectAsState(initial = emptyList())
    
    var selectedTool by remember { mutableStateOf<PdfTool?>(null) }
    var selectedDocuments by remember { mutableStateOf<List<Document>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    
    // External PDFs from file manager
    var externalPdfs by remember { mutableStateOf<List<ExternalPdfFile>>(emptyList()) }
    
    // For page selection (remove/extract pages)
    var selectedDocument by remember { mutableStateOf<Document?>(null) }
    var selectedExternalPdf by remember { mutableStateOf<ExternalPdfFile?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    
    // For watermark
    var watermarkText by remember { mutableStateOf("") }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    
    // For password protection
    var passwordText by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // File picker for external PDFs
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Get file info
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown PDF"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    
                    // Get page count
                    val count = try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                renderer.pageCount
                            }
                        } ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    externalPdfs = externalPdfs + ExternalPdfFile(uri, name, size, count)
                }
            }
        }
    }
    
    // Load page count when document selected
    LaunchedEffect(selectedDocument) {
        selectedDocument?.let { doc ->
            withContext(Dispatchers.IO) {
                pageCount = PdfEditor.getPageCount(doc.pdfPath)
                val bitmaps = PdfEditor.renderAllPagesToBitmap(context, doc.pdfPath)
                pageBitmaps = bitmaps
            }
            selectedPages = emptySet()
        }
    }
    
    // Load page count when external pdf selected
    LaunchedEffect(selectedExternalPdf) {
        selectedExternalPdf?.let { pdf ->
            withContext(Dispatchers.IO) {
                pageCount = pdf.pageCount
                // Render pages from URI
                val bitmaps = mutableListOf<Bitmap>()
                try {
                    context.contentResolver.openFileDescriptor(pdf.uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val bitmap = Bitmap.createBitmap(
                                        page.width,
                                        page.height,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmaps.add(bitmap)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                pageBitmaps = bitmaps
            }
            selectedPages = emptySet()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            if (selectedTool != null) selectedTool!!.label else "PDF Tools",
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTool != null) {
                            Text(
                                selectedTool!!.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedDocument != null || selectedExternalPdf != null -> {
                                selectedDocument = null
                                selectedExternalPdf = null
                                pageBitmaps = emptyList()
                            }
                            selectedTool != null -> {
                                selectedTool = null
                                selectedDocuments = emptyList()
                                externalPdfs = emptyList()
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // Show FAB to add PDFs from file manager when in document selection mode
            if (selectedTool != null && selectedDocument == null && selectedExternalPdf == null) {
                FloatingActionButton(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add PDF from device")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Tool selection screen
                selectedTool == null -> {
                    ToolSelectionGrid(
                        onToolSelected = { tool ->
                            if (tool == PdfTool.OPTIMIZE) {
                                onOptimizeRequested()
                            } else {
                                selectedTool = tool
                                selectedDocuments = emptyList()
                                selectedDocument = null
                                selectedExternalPdf = null
                                externalPdfs = emptyList()
                            }
                        }
                    )
                }
                
                // Page selection for remove/split/extract pages
                (selectedDocument != null || selectedExternalPdf != null) && 
                (selectedTool == PdfTool.REMOVE_PAGES || selectedTool == PdfTool.EXTRACT_IMAGES || selectedTool == PdfTool.SPLIT) -> {
                    PageSelectionScreen(
                        documentName = selectedDocument?.name ?: selectedExternalPdf?.name ?: "",
                        pageCount = pageCount,
                        pageBitmaps = pageBitmaps,
                        selectedPages = selectedPages,
                        onPageToggle = { page ->
                            selectedPages = if (page in selectedPages) {
                                selectedPages - page
                            } else {
                                selectedPages + page
                            }
                        },
                        onSelectAll = {
                            selectedPages = (1..pageCount).toSet()
                        },
                        onDeselectAll = {
                            selectedPages = emptySet()
                        },
                        actionLabel = when (selectedTool) {
                            PdfTool.REMOVE_PAGES -> "Remove Selected"
                            PdfTool.EXTRACT_IMAGES -> "Extract as Images"
                            PdfTool.SPLIT -> "Split PDF"
                            else -> "Action"
                        },
                        onAction = {
                            if (selectedPages.isNotEmpty()) {
                                isProcessing = true
                                processingMessage = when (selectedTool) {
                                    PdfTool.REMOVE_PAGES -> "Removing pages..."
                                    PdfTool.EXTRACT_IMAGES -> "Extracting images..."
                                    PdfTool.SPLIT -> "Splitting PDF..."
                                    else -> "Processing..."
                                }
                                
                                scope.launch {
                                    val pdfPath = selectedDocument?.pdfPath ?: run {
                                        // Copy external PDF to temp file
                                        selectedExternalPdf?.let { external ->
                                            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
                                            context.contentResolver.openInputStream(external.uri)?.use { input ->
                                                tempFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            tempFile.absolutePath
                                        }
                                    }
                                    
                                    if (pdfPath != null) {
                                        when (selectedTool) {
                                            PdfTool.REMOVE_PAGES -> {
                                                val result = withContext(Dispatchers.IO) {
                                                    PdfEditor.removePages(context, pdfPath, selectedPages.toList())
                                                }
                                                
                                                result.onSuccess { newPath ->
                                                    val newPageCount = PdfEditor.getPageCount(newPath)
                                                    val newDoc = Document(
                                                        name = File(newPath).nameWithoutExtension,
                                                        pdfPath = newPath,
                                                        thumbnailPath = selectedDocument?.thumbnailPath,
                                                        pageCount = newPageCount,
                                                        size = PdfGenerator.getFileSize(newPath)
                                                    )
                                                    repository.insertDocument(newDoc)
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Pages removed successfully!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                
                                                result.onFailure { error ->
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            
                                            PdfTool.EXTRACT_IMAGES -> {
                                                val result = withContext(Dispatchers.IO) {
                                                    PdfEditor.extractPagesAsImages(context, pdfPath, selectedPages.toList().sorted())
                                                }
                                                
                                                result.onSuccess { count ->
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "$count images saved to gallery!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                
                                                result.onFailure { error ->
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            
                                            PdfTool.SPLIT -> {
                                                val result = withContext(Dispatchers.IO) {
                                                    PdfEditor.splitPdfByPages(context, pdfPath, selectedPages.toList().sorted())
                                                }
                                                
                                                result.onSuccess { newPath ->
                                                    val newPageCount = PdfEditor.getPageCount(newPath)
                                                    val newDoc = Document(
                                                        name = File(newPath).nameWithoutExtension,
                                                        pdfPath = newPath,
                                                        thumbnailPath = selectedDocument?.thumbnailPath,
                                                        pageCount = newPageCount,
                                                        size = PdfGenerator.getFileSize(newPath)
                                                    )
                                                    repository.insertDocument(newDoc)
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "PDF split successfully!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                
                                                result.onFailure { error ->
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                    
                                    isProcessing = false
                                    selectedDocument = null
                                    selectedExternalPdf = null
                                    selectedTool = null
                                    pageBitmaps = emptyList()
                                }
                            }
                        }
                    )
                }
                
                // Document selection for merge or single document operations
                else -> {
                    EnhancedDocumentSelectionScreen(
                        documents = documents,
                        selectedDocuments = selectedDocuments,
                        externalPdfs = externalPdfs,
                        multiSelect = selectedTool == PdfTool.MERGE,
                        onDocumentToggle = { doc ->
                            when (selectedTool) {
                                PdfTool.MERGE -> {
                                    selectedDocuments = if (doc in selectedDocuments) {
                                        selectedDocuments - doc
                                    } else {
                                        selectedDocuments + doc
                                    }
                                }
                                PdfTool.SPLIT, PdfTool.REMOVE_PAGES, PdfTool.EXTRACT_IMAGES -> {
                                    selectedDocument = doc
                                }
                                PdfTool.OPTIMIZE, PdfTool.ADD_WATERMARK, PdfTool.PASSWORD_PROTECT -> {
                                    selectedDocument = doc
                                    when (selectedTool) {
                                        PdfTool.ADD_WATERMARK -> showWatermarkDialog = true
                                        PdfTool.PASSWORD_PROTECT -> showPasswordDialog = true
                                        PdfTool.OPTIMIZE -> onOptimizeRequested()
                                        else -> {}
                                    }
                                }
                                else -> {}
                            }
                        },
                        onExternalPdfToggle = { pdf ->
                            when (selectedTool) {
                                PdfTool.MERGE -> {
                                    // For merge, add to external list (already there) and toggle selection state
                                }
                                PdfTool.SPLIT, PdfTool.REMOVE_PAGES, PdfTool.EXTRACT_IMAGES -> {
                                    selectedExternalPdf = pdf
                                }
                                else -> {}
                            }
                        },
                        onExternalPdfDelete = { pdf ->
                            externalPdfs = externalPdfs.filter { it.uri != pdf.uri }
                        },
                        onAddFromDevice = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onAction = {
                            when (selectedTool) {
                                PdfTool.MERGE -> {
                                    val totalPdfs = selectedDocuments.size + externalPdfs.size
                                    if (totalPdfs >= 2) {
                                        isProcessing = true
                                        processingMessage = "Merging PDFs..."
                                        
                                        scope.launch {
                                            // Prepare paths - including external PDFs
                                            val allPaths = mutableListOf<String>()
                                            
                                            // Add selected documents
                                            allPaths.addAll(selectedDocuments.map { it.pdfPath })
                                            
                                            // Copy external PDFs to temp files
                                            externalPdfs.forEach { external ->
                                                try {
                                                    val tempFile = File(context.cacheDir, "ext_${System.currentTimeMillis()}_${external.name}")
                                                    context.contentResolver.openInputStream(external.uri)?.use { input ->
                                                        tempFile.outputStream().use { output ->
                                                            input.copyTo(output)
                                                        }
                                                    }
                                                    allPaths.add(tempFile.absolutePath)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            
                                            val result = withContext(Dispatchers.IO) {
                                                PdfEditor.mergePdfs(
                                                    context,
                                                    allPaths,
                                                    "Merged_Document"
                                                )
                                            }
                                            
                                            result.onSuccess { mergedPath ->
                                                val totalPages = selectedDocuments.sumOf { it.pageCount } + externalPdfs.sumOf { it.pageCount }
                                                val mergedDoc = Document(
                                                    name = "Merged Document",
                                                    pdfPath = mergedPath,
                                                    thumbnailPath = selectedDocuments.firstOrNull()?.thumbnailPath,
                                                    pageCount = totalPages,
                                                    size = PdfGenerator.getFileSize(mergedPath)
                                                )
                                                val docId = repository.insertDocument(mergedDoc)
                                                
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "PDFs merged successfully!", Toast.LENGTH_SHORT).show()
                                                    onMergeComplete(docId)
                                                }
                                            }
                                            
                                            result.onFailure { error ->
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Merge failed: ${error.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            
                                            isProcessing = false
                                        }
                                    }
                                }
                                else -> {}
                            }
                        },
                        actionLabel = when (selectedTool) {
                            PdfTool.MERGE -> "Merge ${selectedDocuments.size + externalPdfs.size} PDFs"
                            else -> "Select PDF"
                        },
                        actionEnabled = when (selectedTool) {
                            PdfTool.MERGE -> (selectedDocuments.size + externalPdfs.size) >= 2
                            else -> false
                        },
                        showActionButton = selectedTool == PdfTool.MERGE
                    )
                }
            }
            
            // Loading overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(processingMessage, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            // Watermark Dialog
            if (showWatermarkDialog && selectedDocument != null) {
                AlertDialog(
                    onDismissRequest = { 
                        showWatermarkDialog = false 
                        selectedDocument = null
                    },
                    title = { Text("Add Watermark") },
                    text = {
                        Column {
                            Text("Enter watermark text:", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                placeholder = { Text("e.g., CONFIDENTIAL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (watermarkText.isNotBlank()) {
                                    isProcessing = true
                                    processingMessage = "Adding watermark..."
                                    showWatermarkDialog = false
                                    
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            PdfEditor.addWatermark(context, selectedDocument!!.pdfPath, watermarkText)
                                        }
                                        
                                        result.onSuccess { newPath ->
                                            val newDoc = Document(
                                                name = "${selectedDocument!!.name}_watermarked",
                                                pdfPath = newPath,
                                                thumbnailPath = selectedDocument!!.thumbnailPath,
                                                pageCount = selectedDocument!!.pageCount,
                                                size = PdfGenerator.getFileSize(newPath)
                                            )
                                            repository.insertDocument(newDoc)
                                            Toast.makeText(context, "Watermark added!", Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        result.onFailure { error ->
                                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                        }
                                        
                                        isProcessing = false
                                        selectedDocument = null
                                        selectedTool = null
                                        watermarkText = ""
                                    }
                                }
                            },
                            enabled = watermarkText.isNotBlank()
                        ) {
                            Text("Add Watermark")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showWatermarkDialog = false
                            selectedDocument = null
                            watermarkText = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Password Dialog
            if (showPasswordDialog && selectedDocument != null) {
                AlertDialog(
                    onDismissRequest = { 
                        showPasswordDialog = false 
                        selectedDocument = null
                    },
                    title = { Text("Password Protect PDF") },
                    text = {
                        Column {
                            Text("Enter password:", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passwordText,
                                onValueChange = { passwordText = it },
                                placeholder = { Text("Enter password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (passwordText.isNotBlank()) {
                                    isProcessing = true
                                    processingMessage = "Encrypting PDF..."
                                    showPasswordDialog = false
                                    
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            PdfEditor.passwordProtect(context, selectedDocument!!.pdfPath, passwordText)
                                        }
                                        
                                        result.onSuccess { newPath ->
                                            val newDoc = Document(
                                                name = "${selectedDocument!!.name}_protected",
                                                pdfPath = newPath,
                                                thumbnailPath = selectedDocument!!.thumbnailPath,
                                                pageCount = selectedDocument!!.pageCount,
                                                size = PdfGenerator.getFileSize(newPath)
                                            )
                                            repository.insertDocument(newDoc)
                                            Toast.makeText(context, "PDF password protected!", Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        result.onFailure { error ->
                                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                        }
                                        
                                        isProcessing = false
                                        selectedDocument = null
                                        selectedTool = null
                                        passwordText = ""
                                    }
                                }
                            },
                            enabled = passwordText.isNotBlank()
                        ) {
                            Text("Protect PDF")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showPasswordDialog = false
                            selectedDocument = null
                            passwordText = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolSelectionGrid(onToolSelected: (PdfTool) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Select a Tool",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Edit your PDF documents",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        items(PdfTool.entries) { tool ->
            ToolCard(
                tool = tool,
                onClick = { onToolSelected(tool) }
            )
        }
    }
}

@Composable
private fun ToolCard(
    tool: PdfTool,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gradient icon background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(tool.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tool.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tool.label,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnhancedDocumentSelectionScreen(
    documents: List<Document>,
    selectedDocuments: List<Document>,
    externalPdfs: List<ExternalPdfFile>,
    multiSelect: Boolean,
    onDocumentToggle: (Document) -> Unit,
    onExternalPdfToggle: (ExternalPdfFile) -> Unit,
    onExternalPdfDelete: (ExternalPdfFile) -> Unit,
    onAddFromDevice: () -> Unit,
    onAction: () -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    showActionButton: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // External PDFs section
        if (externalPdfs.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "From Device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = onAddFromDevice) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add More")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    externalPdfs.forEach { pdf ->
                        ExternalPdfCard(
                            pdf = pdf,
                            onDelete = { onExternalPdfDelete(pdf) },
                            onClick = { onExternalPdfToggle(pdf) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        
        // App documents
        if (documents.isEmpty() && externalPdfs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No documents found", fontWeight = FontWeight.Medium)
                    Text(
                        "Tap + to add PDFs from your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onAddFromDevice) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add PDF from Device")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (documents.isNotEmpty()) {
                    item {
                        Text(
                            if (multiSelect) "Select PDFs to merge" else "Select a PDF",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(documents) { doc ->
                        SelectableDocumentCard(
                            document = doc,
                            isSelected = doc in selectedDocuments,
                            onClick = { onDocumentToggle(doc) }
                        )
                    }
                }
            }
        }
        
        // Action button
        if (showActionButton) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExternalPdfCard(
    pdf: ExternalPdfFile,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PDF Icon
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pdf.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${pdf.pageCount} pages • ${PdfGenerator.formatFileSize(pdf.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 3-dot menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableDocumentCard(
    document: Document,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            document.thumbnailPath?.let { path ->
                val bitmap = remember(path) {
                    BitmapFactory.decodeFile(path)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            } ?: run {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${document.pageCount} pages • ${PdfGenerator.formatFileSize(document.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 3-dot menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Select") },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(
                                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageSelectionScreen(
    documentName: String,
    pageCount: Int,
    pageBitmaps: List<Bitmap>,
    selectedPages: Set<Int>,
    onPageToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Document info and selection controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    documentName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${selectedPages.size} of $pageCount pages selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                    OutlinedButton(onClick = onDeselectAll) {
                        Text("Deselect All")
                    }
                }
            }
        }
        
        // Page grid
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rows = (1..pageCount).chunked(3)
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { pageNum ->
                        PageThumbnail(
                            pageNumber = pageNum,
                            bitmap = pageBitmaps.getOrNull(pageNum - 1),
                            isSelected = pageNum in selectedPages,
                            onClick = { onPageToggle(pageNum) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty spaces
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        // Action button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = onAction,
                enabled = selectedPages.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (actionLabel.contains("Remove")) 
                        MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text("$actionLabel (${selectedPages.size})", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PageThumbnail(
    pageNumber: Int,
    bitmap: Bitmap?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.75f),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page $pageNumber",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            
            // Page number badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    "$pageNumber",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Selection checkmark
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
