package com.example.aidocumentscanner.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.aidocumentscanner.DocuScanApplication
import com.example.aidocumentscanner.billing.MonetizationConfig
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.pdf.PageSpecParser
import com.example.aidocumentscanner.pdf.PdfDocumentRegistrar
import com.example.aidocumentscanner.pdf.PdfEditor
import com.example.aidocumentscanner.pdf.PdfToolFileManager
import com.example.aidocumentscanner.util.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase4PdfTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    MERGE("Merge PDFs", "Combine two or more PDFs", Icons.Default.CallMerge),
    SPLIT("Split PDF", "Create PDFs from page groups", Icons.Default.CallSplit),
    REMOVE("Remove pages", "Create a copy without selected pages", Icons.Default.Delete),
    REORDER("Reorder pages", "Enter the complete new page order", Icons.Default.Reorder),
    ROTATE("Rotate pages", "Rotate selected pages without rasterizing", Icons.Default.RotateRight),
    EXTRACT_IMAGES("PDF to images", "Extract selected pages as JPEGs", Icons.Default.Image),
    IMAGES_TO_PDF("Images to PDF", "Use scanner editor for selected images", Icons.Default.PictureAsPdf),
    OPTIMIZE("Optimize PDF", "Safe structure-preserving cleanup", Icons.Default.Compress),
    WATERMARK("Watermark", "Add a visible local watermark", Icons.Default.WaterDrop),
    PASSWORD("Password protect", "Create an encrypted PDF copy", Icons.Default.Lock),
    RENAME("Rename document", "Change the name shown in DocuScan", Icons.Default.Edit)
}

private data class ExternalPdfSelection(
    val info: PdfToolFileManager.ExternalInfo
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit,
    onDocumentCreated: (Long) -> Unit,
    onOptimizeRequested: (Long?) -> Unit,
    onImagesToPdfRequested: (List<Bitmap>) -> Unit,
    onUpgradeToPro: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as DocuScanApplication
    val billingState by app.container.billingManager.state.collectAsState()
    val repository = remember { DocumentRepository(context) }
    val documents by repository.getAllDocuments().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var selectedTool by remember { mutableStateOf<Phase4PdfTool?>(null) }
    var selectedDocument by remember { mutableStateOf<Document?>(null) }
    var externalSelection by remember { mutableStateOf<ExternalPdfSelection?>(null) }
    val mergeDocuments = remember { mutableStateListOf<Document>() }
    val mergeExternal = remember { mutableStateListOf<ExternalPdfSelection>() }

    var pageSpec by remember { mutableStateOf("") }
    var splitSpec by remember { mutableStateOf("") }
    var orderSpec by remember { mutableStateOf("") }
    var watermarkText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(90) }
    var processing by remember { mutableStateOf(false) }
    var externalPageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(externalSelection) {
        val selection = externalSelection
        if (selection != null) {
            withContext(Dispatchers.IO) {
                var temp: File? = null
                try {
                    temp = PdfToolFileManager.copyToToolCache(context, selection.info)
                    externalPageCount = PdfEditor.getPageCount(temp.absolutePath)
                } catch (_: Throwable) {
                    externalPageCount = 0
                } finally {
                    PdfToolFileManager.cleanup(temp)
                }
            }
        } else {
            externalPageCount = 0
        }
    }

    fun resetWorkspace() {
        selectedDocument = null
        externalSelection = null
        mergeDocuments.clear()
        mergeExternal.clear()
        pageSpec = ""
        splitSpec = ""
        orderSpec = ""
        watermarkText = ""
        password = ""
        confirmPassword = ""
        renameText = ""
        rotation = 90
    }

    val singlePdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        externalSelection = ExternalPdfSelection(
            PdfToolFileManager.queryExternalInfo(context, uri)
        )
    }

    val mergePdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val info = PdfToolFileManager.queryExternalInfo(context, uri)
            if (mergeExternal.none { it.info.uri == uri }) {
                mergeExternal += ExternalPdfSelection(info)
            }
        }
    }

    val imagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        processing = true
        scope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { BitmapLoader.decode(context, it) }
            }
            processing = false

            if (bitmaps.isEmpty()) {
                snackbar.showSnackbar("Could not read the selected images")
            } else {
                onImagesToPdfRequested(bitmaps)
            }
        }
    }

    fun selectedSourceName(): String? =
        selectedDocument?.name ?: externalSelection?.info?.displayName

    suspend fun withPreparedSingleSource(
        action: suspend (File) -> Unit
    ) {
        val internal = selectedDocument
        if (internal != null) {
            action(File(internal.pdfPath))
            return
        }

        val external = externalSelection ?: error("Choose a PDF first")
        var temp: File? = null
        try {
            temp = withContext(Dispatchers.IO) {
                PdfToolFileManager.copyToToolCache(context, external.info)
            }
            action(temp)
        } finally {
            PdfToolFileManager.cleanup(temp)
        }
    }

    suspend fun registerAndOpen(outputPath: String) {
        val id = withContext(Dispatchers.IO) {
            PdfDocumentRegistrar.register(
                context,
                repository,
                outputPath,
                File(outputPath).nameWithoutExtension
            )
        }
        onDocumentCreated(id)
    }

    suspend fun performSingleOutput(
        operation: (File) -> Result<String>
    ) {
        processing = true
        try {
            withPreparedSingleSource { source ->
                val output = withContext(Dispatchers.IO) {
                    operation(source).getOrThrow()
                }
                registerAndOpen(output)
            }
        } catch (error: Throwable) {
            snackbar.showSnackbar(error.message ?: "PDF operation failed")
        } finally {
            processing = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedTool?.title ?: "PDF Tools",
                            fontWeight = FontWeight.Bold
                        )
                        selectedTool?.let {
                            Text(
                                it.subtitle,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedTool == null) {
                                onBack()
                            } else {
                                selectedTool = null
                                resetWorkspace()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTool == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(Phase4PdfTool.entries) { tool ->
                        val premium =
                            MonetizationConfig.isPremiumPdfTool(tool.name)

                        PdfToolCard(
                            tool = tool,
                            premium = premium,
                            proOwned = billingState.isPro,
                            onClick = {
                                if (premium && !billingState.isPro) {
                                    onUpgradeToPro()
                                } else {
                                    resetWorkspace()
                                    if (tool == Phase4PdfTool.IMAGES_TO_PDF) {
                                        imagesPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    } else {
                                        selectedTool = tool
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                val tool = selectedTool!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (tool == Phase4PdfTool.MERGE) {
                        item {
                            Text(
                                "Choose at least two PDFs. Their displayed order below is the merge order.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        items(documents, key = { "merge_internal_${it.id}" }) { doc ->
                            SelectableDocumentRow(
                                name = doc.name,
                                selected = mergeDocuments.any { it.id == doc.id },
                                onToggle = {
                                    val existing = mergeDocuments.indexOfFirst { it.id == doc.id }
                                    if (existing >= 0) mergeDocuments.removeAt(existing)
                                    else mergeDocuments += doc
                                }
                            )
                        }

                        items(mergeExternal, key = { it.info.uri.toString() }) { external ->
                            SelectableDocumentRow(
                                name = external.info.displayName,
                                selected = true,
                                onToggle = { mergeExternal.remove(external) }
                            )
                        }

                        item {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    mergePdfPicker.launch(arrayOf("application/pdf"))
                                }
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Add PDFs from device")
                            }
                        }

                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = mergeDocuments.size + mergeExternal.size >= 2 && !processing,
                                onClick = {
                                    scope.launch {
                                        processing = true
                                        val tempFiles = mutableListOf<File>()
                                        try {
                                            val paths = mutableListOf<String>()
                                            paths += mergeDocuments.map { it.pdfPath }

                                            mergeExternal.forEach { external ->
                                                val temp = withContext(Dispatchers.IO) {
                                                    PdfToolFileManager.copyToToolCache(
                                                        context,
                                                        external.info
                                                    )
                                                }
                                                tempFiles += temp
                                                paths += temp.absolutePath
                                            }

                                            val output = withContext(Dispatchers.IO) {
                                                PdfEditor.mergePdfs(
                                                    context,
                                                    paths,
                                                    "Merged_Document"
                                                ).getOrThrow()
                                            }
                                            registerAndOpen(output)
                                        } catch (error: Throwable) {
                                            snackbar.showSnackbar(
                                                error.message ?: "Merge failed"
                                            )
                                        } finally {
                                            tempFiles.forEach(PdfToolFileManager::cleanup)
                                            processing = false
                                        }
                                    }
                                }
                            ) {
                                Text("Merge ${mergeDocuments.size + mergeExternal.size} PDFs")
                            }
                        }
                    } else {
                        if (selectedDocument == null && externalSelection == null) {
                            item {
                                Text(
                                    when (tool) {
                                        Phase4PdfTool.OPTIMIZE ->
                                            "Choose a DocuScan document to optimize."
                                        Phase4PdfTool.RENAME ->
                                            "Choose a DocuScan document to rename."
                                        else ->
                                            "Choose a DocuScan document or a PDF from your device."
                                    }
                                )
                            }

                            items(documents, key = { "single_${it.id}" }) { doc ->
                                Surface(
                                    onClick = {
                                        selectedDocument = doc
                                        renameText = doc.name
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 1.dp
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            doc.name,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${doc.pageCount} pages",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (tool != Phase4PdfTool.OPTIMIZE &&
                                tool != Phase4PdfTool.RENAME
                            ) {
                                item {
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            singlePdfPicker.launch(arrayOf("application/pdf"))
                                        }
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null)
                                        Spacer(Modifier.size(8.dp))
                                        Text("Choose PDF from device")
                                    }
                                }
                            }
                        } else {
                            item {
                                SelectedSourceHeader(
                                    name = selectedSourceName().orEmpty(),
                                    onChange = {
                                        selectedDocument = null
                                        externalSelection = null
                                    }
                                )
                            }

                            val pageCount = selectedDocument?.pageCount ?: externalPageCount

                            when (tool) {
                                Phase4PdfTool.SPLIT -> {
                                    item {
                                        OutlinedTextField(
                                            value = splitSpec,
                                            onValueChange = { splitSpec = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Split groups") },
                                            supportingText = {
                                                Text("Example: 1-3;4-6;7-$pageCount")
                                            }
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !processing,
                                            onClick = {
                                                scope.launch {
                                                    val groups = PageSpecParser.parseSplitGroups(
                                                        splitSpec,
                                                        pageCount
                                                    ).getOrElse {
                                                        snackbar.showSnackbar(
                                                            it.message ?: "Invalid ranges"
                                                        )
                                                        return@launch
                                                    }

                                                    processing = true
                                                    try {
                                                        withPreparedSingleSource { source ->
                                                            val outputs = withContext(Dispatchers.IO) {
                                                                PdfEditor.splitPdfGroups(
                                                                    context,
                                                                    source.absolutePath,
                                                                    groups
                                                                ).getOrThrow()
                                                            }
                                                            val ids = outputs.map { output ->
                                                                withContext(Dispatchers.IO) {
                                                                    PdfDocumentRegistrar.register(
                                                                        context,
                                                                        repository,
                                                                        output
                                                                    )
                                                                }
                                                            }
                                                            snackbar.showSnackbar(
                                                                "Created ${ids.size} split PDFs"
                                                            )
                                                            ids.firstOrNull()?.let(onDocumentCreated)
                                                        }
                                                    } catch (error: Throwable) {
                                                        snackbar.showSnackbar(
                                                            error.message ?: "Split failed"
                                                        )
                                                    } finally {
                                                        processing = false
                                                    }
                                                }
                                            }
                                        ) { Text("Split PDF") }
                                    }
                                }

                                Phase4PdfTool.REMOVE -> {
                                    item {
                                        PageSelectionField(
                                            value = pageSpec,
                                            onValueChange = { pageSpec = it },
                                            pageCount = pageCount,
                                            label = "Pages to remove"
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                scope.launch {
                                                    val pages = PageSpecParser.parseSelection(
                                                        pageSpec,
                                                        pageCount
                                                    ).getOrElse {
                                                        snackbar.showSnackbar(
                                                            it.message ?: "Invalid pages"
                                                        )
                                                        return@launch
                                                    }
                                                    performSingleOutput { source ->
                                                        PdfEditor.removePages(
                                                            context,
                                                            source.absolutePath,
                                                            pages
                                                        )
                                                    }
                                                }
                                            }
                                        ) { Text("Create PDF without these pages") }
                                    }
                                }

                                Phase4PdfTool.REORDER -> {
                                    item {
                                        OutlinedTextField(
                                            value = orderSpec,
                                            onValueChange = { orderSpec = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Complete page order") },
                                            supportingText = {
                                                Text(
                                                    "Enter all $pageCount pages once, e.g. " +
                                                        PageSpecParser.allPagesSpec(pageCount)
                                                )
                                            }
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                scope.launch {
                                                    val order = PageSpecParser.parseOrder(
                                                        orderSpec,
                                                        pageCount
                                                    ).getOrElse {
                                                        snackbar.showSnackbar(
                                                            it.message ?: "Invalid page order"
                                                        )
                                                        return@launch
                                                    }
                                                    performSingleOutput { source ->
                                                        PdfEditor.reorderPages(
                                                            context,
                                                            source.absolutePath,
                                                            order
                                                        )
                                                    }
                                                }
                                            }
                                        ) { Text("Create reordered PDF") }
                                    }
                                }

                                Phase4PdfTool.ROTATE -> {
                                    item {
                                        PageSelectionField(
                                            value = pageSpec,
                                            onValueChange = { pageSpec = it },
                                            pageCount = pageCount,
                                            label = "Pages to rotate"
                                        )
                                    }
                                    item {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(90, 180, 270).forEach { angle ->
                                                FilterChip(
                                                    selected = rotation == angle,
                                                    onClick = { rotation = angle },
                                                    label = { Text("$angle°") }
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                scope.launch {
                                                    val pages = PageSpecParser.parseSelection(
                                                        pageSpec,
                                                        pageCount
                                                    ).getOrElse {
                                                        snackbar.showSnackbar(
                                                            it.message ?: "Invalid pages"
                                                        )
                                                        return@launch
                                                    }
                                                    performSingleOutput { source ->
                                                        PdfEditor.rotatePages(
                                                            context,
                                                            source.absolutePath,
                                                            pages,
                                                            rotation
                                                        )
                                                    }
                                                }
                                            }
                                        ) { Text("Rotate selected pages") }
                                    }
                                }

                                Phase4PdfTool.EXTRACT_IMAGES -> {
                                    item {
                                        PageSelectionField(
                                            value = pageSpec,
                                            onValueChange = { pageSpec = it },
                                            pageCount = pageCount,
                                            label = "Pages to extract"
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                scope.launch {
                                                    val pages = PageSpecParser.parseSelection(
                                                        pageSpec,
                                                        pageCount
                                                    ).getOrElse {
                                                        snackbar.showSnackbar(
                                                            it.message ?: "Invalid pages"
                                                        )
                                                        return@launch
                                                    }

                                                    processing = true
                                                    try {
                                                        withPreparedSingleSource { source ->
                                                            val files = withContext(Dispatchers.IO) {
                                                                PdfEditor.extractPagesAsImageFiles(
                                                                    context,
                                                                    source.absolutePath,
                                                                    pages
                                                                ).getOrThrow()
                                                            }
                                                            shareExtractedImages(context, files)
                                                            snackbar.showSnackbar(
                                                                "Extracted ${files.size} images"
                                                            )
                                                        }
                                                    } catch (error: Throwable) {
                                                        snackbar.showSnackbar(
                                                            error.message ?: "Extraction failed"
                                                        )
                                                    } finally {
                                                        processing = false
                                                    }
                                                }
                                            }
                                        ) { Text("Extract and share images") }
                                    }
                                }

                                Phase4PdfTool.WATERMARK -> {
                                    item {
                                        OutlinedTextField(
                                            value = watermarkText,
                                            onValueChange = { watermarkText = it.take(80) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Watermark text") },
                                            singleLine = true
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = watermarkText.isNotBlank(),
                                            onClick = {
                                                scope.launch {
                                                    performSingleOutput { source ->
                                                        PdfEditor.addWatermark(
                                                            context,
                                                            source.absolutePath,
                                                            watermarkText
                                                        )
                                                    }
                                                }
                                            }
                                        ) { Text("Create watermarked PDF") }
                                    }
                                }

                                Phase4PdfTool.PASSWORD -> {
                                    item {
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Password") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true
                                        )
                                    }
                                    item {
                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = { confirmPassword = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Confirm password") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = password.length >= 6 &&
                                                password == confirmPassword,
                                            onClick = {
                                                scope.launch {
                                                    if (password != confirmPassword) {
                                                        snackbar.showSnackbar(
                                                            "Passwords do not match"
                                                        )
                                                        return@launch
                                                    }
                                                    performSingleOutput { source ->
                                                        PdfEditor.passwordProtect(
                                                            context,
                                                            source.absolutePath,
                                                            password
                                                        )
                                                    }
                                                }
                                            }
                                        ) { Text("Create protected PDF") }
                                    }
                                }

                                Phase4PdfTool.OPTIMIZE -> {
                                    item {
                                        Text(
                                            "Optimization removes unused PDF objects and enables " +
                                                "full compression. It does not intentionally turn " +
                                                "pages into JPEG images."
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                onOptimizeRequested(selectedDocument?.id)
                                            }
                                        ) {
                                            Text("Open safe optimizer")
                                        }
                                    }
                                }

                                Phase4PdfTool.RENAME -> {
                                    item {
                                        OutlinedTextField(
                                            value = renameText,
                                            onValueChange = { renameText = it.take(100) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Document name") },
                                            singleLine = true
                                        )
                                    }
                                    item {
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = selectedDocument != null &&
                                                renameText.isNotBlank(),
                                            onClick = {
                                                val doc = selectedDocument
                                                    ?: return@Button
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        repository.renameDocument(
                                                            doc.id,
                                                            renameText.trim()
                                                        )
                                                    }
                                                    snackbar.showSnackbar("Document renamed")
                                                    selectedTool = null
                                                    resetWorkspace()
                                                }
                                            }
                                        ) { Text("Rename") }
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }

            if (processing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Processing locally…")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfToolCard(
    tool: Phase4PdfTool,
    premium: Boolean,
    proOwned: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                tool.icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.size(14.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(tool.title, fontWeight = FontWeight.SemiBold)
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (premium) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        if (proOwned) "PRO" else "PRO • LOCKED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableDocumentRow(
    name: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
            Text(
                name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectedSourceHeader(
    name: String,
    onChange: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Selected PDF", style = MaterialTheme.typography.labelMedium)
                Text(
                    name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onChange) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun PageSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    pageCount: Int,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = {
            Text("Use commas/ranges, e.g. 1,3,5-8. This PDF has $pageCount pages.")
        },
        singleLine = true
    )
}

private fun shareExtractedImages(
    context: android.content.Context,
    paths: List<String>
) {
    if (paths.isEmpty()) return

    val uris = ArrayList(
        paths.map { path ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }
    )

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share extracted pages"))
    }.onFailure {
        Toast.makeText(context, "No app can share these images", Toast.LENGTH_SHORT).show()
    }
}
