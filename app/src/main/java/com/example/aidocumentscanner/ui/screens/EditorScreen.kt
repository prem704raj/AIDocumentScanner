package com.example.aidocumentscanner.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.scanner.DocumentScanner
import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.scanner.PerspectiveCorrector
import com.example.aidocumentscanner.util.BitmapCache
import com.example.aidocumentscanner.util.safeRecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "EditorScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    pages: List<Bitmap>,
    onAddMorePages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onReorderPages: (Int, Int) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    
    var currentPageIndex by remember { mutableStateOf(0) }
    var selectedFilter by remember { mutableStateOf(ImageEnhancer.FilterType.ORIGINAL) }
    var processedPages by remember { mutableStateOf(pages.toMutableList()) }
    var originalPages by remember { mutableStateOf(pages.toMutableList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showApplyAllDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Safely get current page
    val currentPage = remember(processedPages, currentPageIndex) {
        try {
            processedPages.getOrNull(currentPageIndex.coerceIn(0, processedPages.size - 1))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current page", e)
            null
        }
    }
    
    // Sync pages when they change externally
    LaunchedEffect(pages) {
        if (pages != processedPages) {
            processedPages = pages.toMutableList()
            originalPages = pages.toMutableList()
            if (currentPageIndex >= pages.size) {
                currentPageIndex = (pages.size - 1).coerceAtLeast(0)
            }
        }
    }
    
    // Show error snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Edit Document", fontWeight = FontWeight.Bold)
                        Text(
                            "${processedPages.size} page${if (processedPages.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Apply to All button
                    if (processedPages.size > 1) {
                        IconButton(onClick = { showApplyAllDialog = true }) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Apply to All",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    TextButton(
                        onClick = onContinue,
                        enabled = processedPages.isNotEmpty() && !isProcessing
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            ) {
                // Page thumbnails
                if (processedPages.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        itemsIndexed(processedPages) { index, page ->
                            PageThumbnail(
                                bitmap = page,
                                isSelected = index == currentPageIndex,
                                pageNumber = index + 1,
                                onClick = { currentPageIndex = index },
                                canMoveLeft = index > 0,
                                canMoveRight = index < processedPages.size - 1,
                                onMoveLeft = {
                                    val newList = processedPages.toMutableList()
                                    val item = newList.removeAt(index)
                                    newList.add(index - 1, item)
                                    processedPages = newList
                                    onReorderPages(index, index - 1)
                                    currentPageIndex = if (currentPageIndex == index) index - 1 else currentPageIndex
                                },
                                onMoveRight = {
                                    val newList = processedPages.toMutableList()
                                    val item = newList.removeAt(index)
                                    newList.add(index + 1, item)
                                    processedPages = newList
                                    onReorderPages(index, index + 1)
                                    currentPageIndex = if (currentPageIndex == index) index + 1 else currentPageIndex
                                },
                                onDelete = {
                                if (processedPages.size > 1) {
                                    try {
                                        // Recycle the bitmap before removing
                                        val removedBitmap = processedPages[index]
                                        removedBitmap.safeRecycle()
                                        
                                        onRemovePage(index)
                                        processedPages = processedPages.toMutableList().apply {
                                            removeAt(index)
                                        }
                                        originalPages = originalPages.toMutableList().apply {
                                            if (index < size) removeAt(index)
                                        }
                                        if (currentPageIndex >= processedPages.size) {
                                            currentPageIndex = (processedPages.size - 1).coerceAtLeast(0)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error removing page", e)
                                        errorMessage = "Failed to remove page"
                                    }
                                }
                            }
                            )
                        }
                        
                        item {
                            AddPageButton(onClick = onAddMorePages)
                        }
                    }
                    
                    HorizontalDivider()
                }
                
                // Filter options with horizontal scroll
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
                
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    val filters = listOf(
                        "Original" to ImageEnhancer.FilterType.ORIGINAL,
                        "Magic" to ImageEnhancer.FilterType.MAGIC_COLOR,
                        "B&W" to ImageEnhancer.FilterType.BLACK_WHITE,
                        "Gray" to ImageEnhancer.FilterType.GRAYSCALE,
                        "Sharpen" to ImageEnhancer.FilterType.SHARPEN,
                        "Contrast" to ImageEnhancer.FilterType.HIGH_CONTRAST,
                        "Sepia" to ImageEnhancer.FilterType.SEPIA,
                        "Invert" to ImageEnhancer.FilterType.INVERT,
                        "Warm" to ImageEnhancer.FilterType.WARM,
                        "Cool" to ImageEnhancer.FilterType.COOL,
                        "Lighten" to ImageEnhancer.FilterType.LIGHTEN,
                        "Darken" to ImageEnhancer.FilterType.DARKEN
                    )
                    
                    filters.forEach { (name, filter) ->
                        item {
                            FilterOption(
                                name = name,
                                isSelected = selectedFilter == filter,
                                onClick = {
                                    selectedFilter = filter
                                    scope.launch {
                                        isProcessing = true
                                        try {
                                            val result = withContext(Dispatchers.Default) {
                                                val original = originalPages.getOrNull(currentPageIndex)
                                                original?.let { ImageEnhancer.applyFilter(it, filter) }
                                            }
                                            result?.let { newPage ->
                                                processedPages = processedPages.toMutableList().apply {
                                                    if (currentPageIndex < size) {
                                                        set(currentPageIndex, newPage)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error applying filter", e)
                                            errorMessage = "Failed to apply filter"
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Edit actions row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditActionButton(
                        icon = Icons.Default.RotateLeft,
                        label = "Left",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                try {
                                    val rotated = withContext(Dispatchers.Default) {
                                        processedPages.getOrNull(currentPageIndex)?.let { page ->
                                            ImageEnhancer.rotate(page, -90f)
                                        }
                                    }
                                    rotated?.let { newPage ->
                                        processedPages = processedPages.toMutableList().apply {
                                            set(currentPageIndex, newPage)
                                        }
                                        // Also rotate original
                                        originalPages.getOrNull(currentPageIndex)?.let { original ->
                                            val rotatedOriginal = ImageEnhancer.rotate(original, -90f)
                                            originalPages = originalPages.toMutableList().apply {
                                                set(currentPageIndex, rotatedOriginal)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error rotating", e)
                                    errorMessage = "Failed to rotate"
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    )
                    
                    EditActionButton(
                        icon = Icons.Default.RotateRight,
                        label = "Right",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                try {
                                    val rotated = withContext(Dispatchers.Default) {
                                        processedPages.getOrNull(currentPageIndex)?.let { page ->
                                            ImageEnhancer.rotate(page, 90f)
                                        }
                                    }
                                    rotated?.let { newPage ->
                                        processedPages = processedPages.toMutableList().apply {
                                            set(currentPageIndex, newPage)
                                        }
                                        // Also rotate original
                                        originalPages.getOrNull(currentPageIndex)?.let { original ->
                                            val rotatedOriginal = ImageEnhancer.rotate(original, 90f)
                                            originalPages = originalPages.toMutableList().apply {
                                                set(currentPageIndex, rotatedOriginal)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error rotating", e)
                                    errorMessage = "Failed to rotate"
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    )
                    
                    EditActionButton(
                        icon = Icons.Default.Crop,
                        label = "Crop",
                        onClick = { showCropDialog = true }
                    )
                    
                    EditActionButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "AI Crop",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                try {
                                    val cropped = withContext(Dispatchers.Default) {
                                        processedPages.getOrNull(currentPageIndex)?.let { page ->
                                            val result = DocumentScanner.detectDocumentEdges(page)
                                            if (result.confidence > 0.3f) {
                                                PerspectiveCorrector.correctPerspective(page, result.corners)
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                    if (cropped != null) {
                                        processedPages = processedPages.toMutableList().apply {
                                            set(currentPageIndex, cropped)
                                        }
                                        originalPages = originalPages.toMutableList().apply {
                                            set(currentPageIndex, cropped)
                                        }
                                        scope.launch { snackbarHostState.showSnackbar("Document detected and cropped!") }
                                    } else {
                                        scope.launch { snackbarHostState.showSnackbar("No document edges detected. Try manual crop.") }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error with AI crop", e)
                                    errorMessage = "AI crop failed"
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    )
                    
                    EditActionButton(
                        icon = Icons.Default.Add,
                        label = "Add",
                        onClick = onAddMorePages
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            when {
                isProcessing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Processing...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                currentPage != null -> {
                    Image(
                        bitmap = currentPage.asImageBitmap(),
                        contentDescription = "Document page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ImageNotSupported,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No pages to display",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onAddMorePages) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Pages")
                        }
                    }
                }
            }
            
            // Page indicator
            if (processedPages.size > 1 && !isProcessing) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "Page ${currentPageIndex + 1} of ${processedPages.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
    
    // Crop Dialog
    if (showCropDialog && currentPage != null) {
        CropDialog(
            bitmap = currentPage,
            onDismiss = { showCropDialog = false },
            onCrop = { left, top, width, height ->
                scope.launch {
                    isProcessing = true
                    try {
                        val cropped = withContext(Dispatchers.Default) {
                            ImageEnhancer.crop(currentPage, left, top, width, height)
                        }
                        processedPages = processedPages.toMutableList().apply {
                            set(currentPageIndex, cropped)
                        }
                        originalPages = originalPages.toMutableList().apply {
                            set(currentPageIndex, cropped)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cropping", e)
                        errorMessage = "Failed to crop"
                    } finally {
                        isProcessing = false
                        showCropDialog = false
                    }
                }
            }
        )
    }
    
    // Apply to All Dialog
    if (showApplyAllDialog) {
        AlertDialog(
            onDismissRequest = { showApplyAllDialog = false },
            icon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
            title = { Text("Apply to All Pages") },
            text = { 
                Text("Apply the current filter (${selectedFilter.name.replace("_", " ")}) to all ${processedPages.size} pages?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApplyAllDialog = false
                        scope.launch {
                            isProcessing = true
                            try {
                                val newPages = withContext(Dispatchers.Default) {
                                    originalPages.map { page ->
                                        ImageEnhancer.applyFilter(page, selectedFilter)
                                    }
                                }
                                processedPages = newPages.toMutableList()
                                scope.launch { snackbarHostState.showSnackbar("Applied to all pages!") }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error applying to all", e)
                                errorMessage = "Failed to apply to all pages"
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onCrop: (left: Int, top: Int, width: Int, height: Int) -> Unit
) {
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { 
        mutableStateOf(
            CropRect(
                left = 0.1f,
                top = 0.1f,
                right = 0.9f,
                bottom = 0.9f
            )
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop Image") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .onSizeChanged { imageSize = it }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Crop preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Crop overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = cropRect.left * size.width
                    val top = cropRect.top * size.height
                    val right = cropRect.right * size.width
                    val bottom = cropRect.bottom * size.height
                    
                    // Dim areas outside crop
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(size.width, top)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, bottom),
                        size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(left, bottom - top)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(right, top),
                        size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top)
                    )
                    
                    // Crop border
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
                
                // Draggable corners
                CropHandle(
                    offset = Offset(cropRect.left * imageSize.width, cropRect.top * imageSize.height),
                    onDrag = { delta ->
                        cropRect = cropRect.copy(
                            left = (cropRect.left + delta.x / imageSize.width).coerceIn(0f, cropRect.right - 0.1f),
                            top = (cropRect.top + delta.y / imageSize.height).coerceIn(0f, cropRect.bottom - 0.1f)
                        )
                    }
                )
                CropHandle(
                    offset = Offset(cropRect.right * imageSize.width, cropRect.top * imageSize.height),
                    onDrag = { delta ->
                        cropRect = cropRect.copy(
                            right = (cropRect.right + delta.x / imageSize.width).coerceIn(cropRect.left + 0.1f, 1f),
                            top = (cropRect.top + delta.y / imageSize.height).coerceIn(0f, cropRect.bottom - 0.1f)
                        )
                    }
                )
                CropHandle(
                    offset = Offset(cropRect.left * imageSize.width, cropRect.bottom * imageSize.height),
                    onDrag = { delta ->
                        cropRect = cropRect.copy(
                            left = (cropRect.left + delta.x / imageSize.width).coerceIn(0f, cropRect.right - 0.1f),
                            bottom = (cropRect.bottom + delta.y / imageSize.height).coerceIn(cropRect.top + 0.1f, 1f)
                        )
                    }
                )
                CropHandle(
                    offset = Offset(cropRect.right * imageSize.width, cropRect.bottom * imageSize.height),
                    onDrag = { delta ->
                        cropRect = cropRect.copy(
                            right = (cropRect.right + delta.x / imageSize.width).coerceIn(cropRect.left + 0.1f, 1f),
                            bottom = (cropRect.bottom + delta.y / imageSize.height).coerceIn(cropRect.top + 0.1f, 1f)
                        )
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val left = (cropRect.left * bitmap.width).toInt()
                    val top = (cropRect.top * bitmap.height).toInt()
                    val width = ((cropRect.right - cropRect.left) * bitmap.width).toInt()
                    val height = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt()
                    onCrop(left, top, width, height)
                }
            ) {
                Text("Crop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Composable
private fun CropHandle(
    offset: Offset,
    onDrag: (Offset) -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (offset.x - 12.dp.toPx()).toDp() },
                y = with(density) { (offset.y - 12.dp.toPx()).toDp() }
            )
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(Offset(dragAmount.x, dragAmount.y))
                }
            }
    )
}

@Composable
private fun PageThumbnail(
    bitmap: Bitmap,
    isSelected: Boolean,
    pageNumber: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    canMoveLeft: Boolean = false,
    canMoveRight: Boolean = false,
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .height(90.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Move left button
        if (canMoveLeft && isSelected) {
            IconButton(
                onClick = onMoveLeft,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Move left",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .size(70.dp, 90.dp)
                .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .padding(2.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove page",
                        tint = Color.White,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
            
            // Page number badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
            ) {
                Text(
                    text = pageNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        
        // Move right button
        if (canMoveRight && isSelected) {
            IconButton(
                onClick = onMoveRight,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Move right",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddPageButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(70.dp, 90.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add page",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FilterOption(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { 
            Text(
                name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingIcon = if (isSelected) {
            { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun EditActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
