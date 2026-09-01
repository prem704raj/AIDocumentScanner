package com.example.aidocumentscanner.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.scanner.DocumentScanner
import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.scanner.PerspectiveCorrector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun EditorScreen(
    pages: List<Bitmap>,
    onAddMorePages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onPageUpdated: (Int, Bitmap) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onReorderPages: (Int, Int) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var currentPage by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(ImageEnhancer.FilterType.ORIGINAL) }
    var isProcessing by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }

    // Filter baselines are kept separately so switching filters does not repeatedly filter
    // an already-filtered image. The shared 'pages' list remains the single save source.
    val filterBases = remember { mutableStateListOf<Bitmap>().apply { addAll(pages) } }

    LaunchedEffect(pages.size) {
        while (filterBases.size < pages.size) {
            filterBases.add(pages[filterBases.size])
        }
        while (filterBases.size > pages.size) {
            filterBases.removeAt(filterBases.lastIndex)
        }
        if (pages.isEmpty()) currentPage = 0
        else currentPage = currentPage.coerceIn(0, pages.lastIndex)
    }

    val visiblePage = pages.getOrNull(currentPage)

    fun replaceCurrent(bitmap: Bitmap, replaceFilterBase: Boolean) {
        if (currentPage !in pages.indices) return
        if (replaceFilterBase && currentPage in filterBases.indices) {
            filterBases[currentPage] = bitmap
        }
        onPageUpdated(currentPage, bitmap)
    }

    suspend fun processCurrent(block: suspend () -> Bitmap?) {
        if (currentPage !in pages.indices) return
        isProcessing = true
        try {
            val result = block()
            if (result != null) {
                onPageUpdated(currentPage, result)
            }
        } catch (t: Throwable) {
            snackbar.showSnackbar(t.message ?: "Could not edit this page")
        } finally {
            isProcessing = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit Document", fontWeight = FontWeight.Bold)
                        Text(
                            "${pages.size} page${if (pages.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (pages.size > 1 && selectedFilter != ImageEnhancer.FilterType.ORIGINAL) {
                        IconButton(
                            enabled = !isProcessing,
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        pages.indices.forEach { index ->
                                            val base = filterBases.getOrNull(index) ?: pages[index]
                                            val output = withContext(Dispatchers.Default) {
                                                ImageEnhancer.applyFilter(base, selectedFilter)
                                            }
                                            onPageUpdated(index, output)
                                        }
                                        snackbar.showSnackbar("Filter applied to all pages")
                                    } catch (t: Throwable) {
                                        snackbar.showSnackbar("Could not apply filter to all pages")
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Apply filter to all")
                        }
                    }

                    TextButton(
                        enabled = pages.isNotEmpty() && !isProcessing,
                        onClick = onContinue
                    ) {
                        Text("Continue")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pages.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages, key = { index, _ -> index }) { index, bitmap ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier
                                    .size(width = 72.dp, height = 96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (index == currentPage) 2.dp else 1.dp,
                                        color = if (index == currentPage) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { currentPage = index },
                                contentScale = ContentScale.Crop
                            )

                            Row {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        if (index > 0) {
                                            val base = filterBases.removeAt(index)
                                            filterBases.add(index - 1, base)
                                            onReorderPages(index, index - 1)
                                            currentPage = index - 1
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Move left")
                                }
                                IconButton(
                                    enabled = index < pages.lastIndex,
                                    onClick = {
                                        if (index < pages.lastIndex) {
                                            val base = filterBases.removeAt(index)
                                            filterBases.add(index + 1, base)
                                            onReorderPages(index, index + 1)
                                            currentPage = index + 1
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Move right")
                                }
                                IconButton(
                                    enabled = pages.size > 1,
                                    onClick = {
                                        if (pages.size > 1) {
                                            if (index in filterBases.indices) filterBases.removeAt(index)
                                            onRemovePage(index)
                                            currentPage = currentPage.coerceAtMost((pages.size - 2).coerceAtLeast(0))
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete page")
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isProcessing -> CircularProgressIndicator()
                    visiblePage != null -> Image(
                        bitmap = visiblePage.asImageBitmap(),
                        contentDescription = "Current document page",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                    else -> Text("No page selected")
                }
            }

            Text(
                "Filters",
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                fontWeight = FontWeight.SemiBold
            )

            val filters = listOf(
                "Original" to ImageEnhancer.FilterType.ORIGINAL,
                "Magic" to ImageEnhancer.FilterType.MAGIC_COLOR,
                "B&W" to ImageEnhancer.FilterType.BLACK_WHITE,
                "Gray" to ImageEnhancer.FilterType.GRAYSCALE,
                "Sharpen" to ImageEnhancer.FilterType.SHARPEN,
                "Contrast" to ImageEnhancer.FilterType.HIGH_CONTRAST
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { (label, filter) ->
                    item {
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = {
                                selectedFilter = filter
                                val index = currentPage
                                if (index in pages.indices) {
                                    scope.launch {
                                        isProcessing = true
                                        try {
                                            val base = filterBases.getOrNull(index) ?: pages[index]
                                            val output = withContext(Dispatchers.Default) {
                                                if (filter == ImageEnhancer.FilterType.ORIGINAL) {
                                                    base.copy(base.config ?: Bitmap.Config.ARGB_8888, true)
                                                } else {
                                                    ImageEnhancer.applyFilter(base, filter)
                                                }
                                            }
                                            onPageUpdated(index, output)
                                        } catch (t: Throwable) {
                                            snackbar.showSnackbar("Could not apply filter")
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EditorAction("Left", Icons.Default.RotateLeft) {
                    val index = currentPage
                    if (index in pages.indices) {
                        scope.launch {
                            isProcessing = true
                            try {
                                val base = filterBases.getOrNull(index) ?: pages[index]
                                val rotatedBase = withContext(Dispatchers.Default) {
                                    ImageEnhancer.rotate(base, -90f)
                                }
                                filterBases[index] = rotatedBase
                                val displayed = withContext(Dispatchers.Default) {
                                    if (selectedFilter == ImageEnhancer.FilterType.ORIGINAL) rotatedBase
                                    else ImageEnhancer.applyFilter(rotatedBase, selectedFilter)
                                }
                                onPageUpdated(index, displayed)
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }

                EditorAction("Right", Icons.Default.RotateRight) {
                    val index = currentPage
                    if (index in pages.indices) {
                        scope.launch {
                            isProcessing = true
                            try {
                                val base = filterBases.getOrNull(index) ?: pages[index]
                                val rotatedBase = withContext(Dispatchers.Default) {
                                    ImageEnhancer.rotate(base, 90f)
                                }
                                filterBases[index] = rotatedBase
                                val displayed = withContext(Dispatchers.Default) {
                                    if (selectedFilter == ImageEnhancer.FilterType.ORIGINAL) rotatedBase
                                    else ImageEnhancer.applyFilter(rotatedBase, selectedFilter)
                                }
                                onPageUpdated(index, displayed)
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }

                EditorAction("Crop", Icons.Default.Crop) {
                    if (visiblePage != null) showCrop = true
                }

                EditorAction("Auto", Icons.Default.AutoFixHigh) {
                    val index = currentPage
                    if (index in pages.indices) {
                        scope.launch {
                            isProcessing = true
                            try {
                                val base = filterBases.getOrNull(index) ?: pages[index]
                                val cropped = withContext(Dispatchers.Default) {
                                    val detection = DocumentScanner.detectDocumentEdges(base)
                                    if (detection.confidence > 0.3f && detection.corners.size == 4) {
                                        PerspectiveCorrector.correctPerspective(base, detection.corners)
                                    } else {
                                        null
                                    }
                                }

                                if (cropped == null) {
                                    snackbar.showSnackbar("No reliable document edges detected")
                                } else {
                                    filterBases[index] = cropped
                                    val displayed = withContext(Dispatchers.Default) {
                                        if (selectedFilter == ImageEnhancer.FilterType.ORIGINAL) cropped
                                        else ImageEnhancer.applyFilter(cropped, selectedFilter)
                                    }
                                    onPageUpdated(index, displayed)
                                }
                            } catch (t: Throwable) {
                                snackbar.showSnackbar("Automatic crop failed")
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }

                EditorAction("Add", Icons.Default.Add, onAddMorePages)
            }
        }
    }

    if (showCrop && visiblePage != null) {
        NormalizedCropDialog(
            bitmap = filterBases.getOrNull(currentPage) ?: visiblePage,
            onDismiss = { showCrop = false },
            onApply = { left, top, right, bottom ->
                val index = currentPage
                showCrop = false
                if (index in pages.indices) {
                    scope.launch {
                        isProcessing = true
                        try {
                            val base = filterBases.getOrNull(index) ?: pages[index]
                            val x = (left * base.width).roundToInt().coerceIn(0, base.width - 1)
                            val y = (top * base.height).roundToInt().coerceIn(0, base.height - 1)
                            val rightPx = (right * base.width).roundToInt().coerceIn(x + 1, base.width)
                            val bottomPx = (bottom * base.height).roundToInt().coerceIn(y + 1, base.height)
                            val width = rightPx - x
                            val height = bottomPx - y

                            val cropped = withContext(Dispatchers.Default) {
                                ImageEnhancer.crop(base, x, y, width, height)
                            }
                            filterBases[index] = cropped
                            val displayed = withContext(Dispatchers.Default) {
                                if (selectedFilter == ImageEnhancer.FilterType.ORIGINAL) cropped
                                else ImageEnhancer.applyFilter(cropped, selectedFilter)
                            }
                            onPageUpdated(index, displayed)
                        } catch (t: Throwable) {
                            snackbar.showSnackbar("Crop failed")
                        } finally {
                            isProcessing = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun EditorAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NormalizedCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (Float, Float, Float, Float) -> Unit
) {
    var left by remember(bitmap) { mutableFloatStateOf(0f) }
    var top by remember(bitmap) { mutableFloatStateOf(0f) }
    var right by remember(bitmap) { mutableFloatStateOf(1f) }
    var bottom by remember(bitmap) { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop page") },
        text = {
            Column {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Crop preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Fit
                )
                Text("Left")
                Slider(
                    value = left,
                    onValueChange = { left = it.coerceAtMost(right - 0.05f) },
                    valueRange = 0f..1f
                )
                Text("Top")
                Slider(
                    value = top,
                    onValueChange = { top = it.coerceAtMost(bottom - 0.05f) },
                    valueRange = 0f..1f
                )
                Text("Right")
                Slider(
                    value = right,
                    onValueChange = { right = it.coerceAtLeast(left + 0.05f) },
                    valueRange = 0f..1f
                )
                Text("Bottom")
                Slider(
                    value = bottom,
                    onValueChange = { bottom = it.coerceAtLeast(top + 0.05f) },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(left, top, right, bottom) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
