package com.example.aidocumentscanner.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.scanner.DocumentScanner
import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.scanner.PerspectiveCorrector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun EditorScreen(
    pages: List<Bitmap>,
    onAddMorePages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onPageUpdated: (Int, Bitmap) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onReorderPages: (Int, Int) -> Unit = { _, _ -> },
    onDuplicatePage: (Int) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var currentPage by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(ImageEnhancer.FilterType.ORIGINAL) }
    var isProcessing by remember { mutableStateOf(false) }
    var showManualCrop by remember { mutableStateOf(false) }

    val filterBaselines = remember { mutableStateListOf<Bitmap>() }

    LaunchedEffect(pages.size) {
        while (filterBaselines.size < pages.size) {
            filterBaselines.add(pages[filterBaselines.size])
        }
        while (filterBaselines.size > pages.size) {
            filterBaselines.removeAt(filterBaselines.lastIndex)
        }
        if (currentPage > pages.lastIndex) {
            currentPage = pages.lastIndex.coerceAtLeast(0)
        }
    }

    fun replaceCurrent(
        newBitmap: Bitmap,
        updateBaseline: Boolean
    ) {
        val index = currentPage
        if (index !in pages.indices) {
            if (!newBitmap.isRecycled) newBitmap.recycle()
            return
        }

        if (updateBaseline) {
            while (filterBaselines.size <= index) {
                filterBaselines.add(pages[index])
            }
            filterBaselines[index] = newBitmap
            selectedFilter = ImageEnhancer.FilterType.ORIGINAL
        }
        onPageUpdated(index, newBitmap)
    }

    fun processCurrent(
        updateBaseline: Boolean = true,
        block: (Bitmap) -> Bitmap
    ) {
        val source = pages.getOrNull(currentPage) ?: return
        scope.launch {
            isProcessing = true
            try {
                val result = withContext(Dispatchers.Default) { block(source) }
                replaceCurrent(result, updateBaseline)
            } catch (_: Throwable) {
                snackbar.showSnackbar("Could not process this page")
            } finally {
                isProcessing = false
            }
        }
    }

    val visibleFilters = listOf(
        "Original" to ImageEnhancer.FilterType.ORIGINAL,
        "Document" to ImageEnhancer.FilterType.DOCUMENT,
        "B&W" to ImageEnhancer.FilterType.BLACK_WHITE,
        "Grayscale" to ImageEnhancer.FilterType.GRAYSCALE,
        "Color" to ImageEnhancer.FilterType.COLOR_ENHANCE
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit scan", fontWeight = FontWeight.Bold)
                        Text(
                            "${pages.size} page${if (pages.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Delete, contentDescription = "Close editor")
                    }
                },
                actions = {
                    TextButton(
                        enabled = pages.isNotEmpty() && !isProcessing,
                        onClick = onContinue
                    ) {
                        Text("Continue")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (pages.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pages) { index, page ->
                            Box(
                                modifier = Modifier
                                    .size(width = 58.dp, height = 76.dp)
                                    .border(
                                        width = if (index == currentPage) 2.dp else 1.dp,
                                        color = if (index == currentPage) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        currentPage = index
                                        selectedFilter = ImageEnhancer.FilterType.ORIGINAL
                                    }
                            ) {
                                Image(
                                    page.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    "${index + 1}",
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 4.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleFilters.forEach { (label, filter) ->
                        item {
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = {
                                    val index = currentPage
                                    val baseline = filterBaselines.getOrNull(index)
                                        ?: pages.getOrNull(index)
                                        ?: return@FilterChip

                                    selectedFilter = filter
                                    scope.launch {
                                        isProcessing = true
                                        try {
                                            val result = withContext(Dispatchers.Default) {
                                                ImageEnhancer.applyFilter(baseline, filter)
                                            }
                                            onPageUpdated(index, result)
                                        } catch (_: Throwable) {
                                            snackbar.showSnackbar("Filter failed")
                                        } finally {
                                            isProcessing = false
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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditorAction(Icons.Default.RotateLeft, "Left") {
                        processCurrent { ImageEnhancer.rotate(it, -90f) }
                    }
                    EditorAction(Icons.Default.RotateRight, "Right") {
                        processCurrent { ImageEnhancer.rotate(it, 90f) }
                    }
                    EditorAction(Icons.Default.Crop, "Corners") {
                        showManualCrop = true
                    }
                    EditorAction(Icons.Default.AutoFixHigh, "Auto crop") {
                        val source = pages.getOrNull(currentPage) ?: return@EditorAction
                        scope.launch {
                            isProcessing = true
                            try {
                                val result = withContext(Dispatchers.Default) {
                                    DocumentScanner.autoCrop(source, 0.45f)
                                }
                                if (result.wasCropped) {
                                    replaceCurrent(result.bitmap, true)
                                } else {
                                    if (!result.bitmap.isRecycled) result.bitmap.recycle()
                                    snackbar.showSnackbar(
                                        "Edges were uncertain. Adjust the four corners manually."
                                    )
                                }
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                    EditorAction(Icons.Default.ContentCopy, "Duplicate") {
                        onDuplicatePage(currentPage)
                    }
                    EditorAction(Icons.Default.Delete, "Delete") {
                        if (pages.size <= 1) {
                            scope.launch { snackbar.showSnackbar("A document needs one page") }
                        } else {
                            onRemovePage(currentPage)
                            filterBaselines.removeAt(
                                currentPage.coerceAtMost(filterBaselines.lastIndex)
                            )
                            currentPage = currentPage.coerceAtMost(pages.size - 2)
                        }
                    }
                    EditorAction(Icons.Default.Add, "Add") {
                        onAddMorePages()
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            pages.getOrNull(currentPage)?.let { page ->
                Image(
                    page.asImageBitmap(),
                    contentDescription = "Current document page",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }

            if (isProcessing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showManualCrop) {
        pages.getOrNull(currentPage)?.let { bitmap ->
            FourCornerCropDialog(
                bitmap = bitmap,
                onDismiss = { showManualCrop = false },
                onApply = { points ->
                    showManualCrop = false
                    scope.launch {
                        isProcessing = true
                        try {
                            val corrected = withContext(Dispatchers.Default) {
                                PerspectiveCorrector.correctPerspective(bitmap, points)
                            }
                            replaceCurrent(corrected, true)
                        } catch (_: Throwable) {
                            snackbar.showSnackbar("Could not apply corner crop")
                        } finally {
                            isProcessing = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EditorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(54.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun FourCornerCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (List<PointF>) -> Unit
) {
    var corners by remember(bitmap) {
        mutableStateOf(
            listOf(
                Offset(0.06f, 0.06f),
                Offset(0.94f, 0.06f),
                Offset(0.94f, 0.94f),
                Offset(0.06f, 0.94f)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust document corners") },
        text = {
            Column {
                Text(
                    "Drag each handle to the real page corner.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                ) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "Crop preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bitmap) {
                                var active = -1
                                detectDragGestures(
                                    onDragStart = { start ->
                                        active = corners.indices.minByOrNull { index ->
                                            val p = Offset(
                                                corners[index].x * size.width,
                                                corners[index].y * size.height
                                            )
                                            hypot(
                                                (p.x - start.x).toDouble(),
                                                (p.y - start.y).toDouble()
                                            )
                                        } ?: -1
                                    },
                                    onDragEnd = { active = -1 },
                                    onDragCancel = { active = -1 },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        if (active !in 0..3) return@detectDragGestures
                                        val current = corners[active]
                                        val moved = Offset(
                                            (current.x + drag.x / size.width)
                                                .coerceIn(0.01f, 0.99f),
                                            (current.y + drag.y / size.height)
                                                .coerceIn(0.01f, 0.99f)
                                        )
                                        corners = constrainCorner(corners, active, moved)
                                    }
                                )
                            }
                    ) {
                        val px = corners.map {
                            Offset(it.x * size.width, it.y * size.height)
                        }
                        val path = Path().apply {
                            moveTo(px[0].x, px[0].y)
                            lineTo(px[1].x, px[1].y)
                            lineTo(px[2].x, px[2].y)
                            lineTo(px[3].x, px[3].y)
                            close()
                        }
                        drawPath(
                            path,
                            color = Color(0xFF3B82F6),
                            style = Stroke(width = 5f)
                        )
                        px.forEach {
                            drawCircle(
                                color = Color.White,
                                radius = 18f,
                                center = it
                            )
                            drawCircle(
                                color = Color(0xFF3B82F6),
                                radius = 18f,
                                center = it,
                                style = Stroke(width = 5f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        corners.map {
                            PointF(
                                it.x * bitmap.width.toFloat(),
                                it.y * bitmap.height.toFloat()
                            )
                        }
                    )
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun constrainCorner(
    corners: List<Offset>,
    index: Int,
    proposed: Offset
): List<Offset> {
    val margin = 0.035f
    val p = when (index) {
        0 -> Offset(
            proposed.x.coerceAtMost(corners[1].x - margin),
            proposed.y.coerceAtMost(corners[3].y - margin)
        )
        1 -> Offset(
            proposed.x.coerceAtLeast(corners[0].x + margin),
            proposed.y.coerceAtMost(corners[2].y - margin)
        )
        2 -> Offset(
            proposed.x.coerceAtLeast(corners[3].x + margin),
            proposed.y.coerceAtLeast(corners[1].y + margin)
        )
        3 -> Offset(
            proposed.x.coerceAtMost(corners[2].x - margin),
            proposed.y.coerceAtLeast(corners[0].y + margin)
        )
        else -> proposed
    }
    return corners.toMutableList().also { it[index] = p }
}
