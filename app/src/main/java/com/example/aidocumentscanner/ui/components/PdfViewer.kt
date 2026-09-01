package com.example.aidocumentscanner.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.max

private const val DEFAULT_RENDER_WIDTH = 1400
private const val MAX_RENDER_PIXELS = 4_500_000L

/**
 * Lazy PDF viewer.
 *
 * Unlike the previous implementation, this never renders every page up front. LazyColumn composes
 * only a small window around the viewport and each visible page owns a single bounded bitmap.
 */
@Composable
fun InAppPdfViewer(
    pdfUri: Uri,
    modifier: Modifier = Modifier,
    initialPage: Int = 0
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var session by remember(pdfUri) { mutableStateOf<PdfRenderSession?>(null) }
    var loading by remember(pdfUri) { mutableStateOf(true) }
    var error by remember(pdfUri) { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pdfUri) {
        loading = true
        error = null
        session?.close()
        session = null

        val result = withContext(Dispatchers.IO) {
            runCatching { PdfRenderSession.open(context, pdfUri) }
        }
        result.onSuccess { opened ->
            session = opened
            val target = initialPage.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
            currentPage = target
            if (opened.pageCount > 0) listState.scrollToItem(target)
        }.onFailure { throwable ->
            error = throwable.message ?: "Unable to open PDF"
        }
        loading = false
    }

    DisposableEffect(pdfUri) {
        onDispose {
            session?.close()
            session = null
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { currentPage = it }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
    ) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading PDF...")
                    }
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Could not open PDF",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            session != null -> {
                val opened = session!!
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = opened.pageCount,
                        key = { it }
                    ) { pageIndex ->
                        LazyPdfPage(
                            session = opened,
                            pageIndex = pageIndex,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (opened.pageCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "Page ${currentPage + 1} of ${opened.pageCount}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyPdfPage(
    session: PdfRenderSession,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(session, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(session, pageIndex) { mutableStateOf(false) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    val requestedWidth = remember {
        context.resources.displayMetrics.widthPixels
            .coerceAtLeast(720)
            .coerceAtMost(DEFAULT_RENDER_WIDTH)
    }

    LaunchedEffect(session, pageIndex, requestedWidth) {
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        bitmap = null
        failed = false

        val rendered = withContext(Dispatchers.IO) {
            runCatching { session.renderPage(pageIndex, requestedWidth) }.getOrNull()
        }
        if (rendered == null) failed = true else bitmap = rendered
    }

    DisposableEffect(session, pageIndex) {
        onDispose {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
            bitmap = null
        }
    }

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.White)
            .pointerInput(pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 3f)
                    if (scale > 1f) {
                        val maxPanX = size.width * (scale - 1f) / 2f
                        val maxPanY = size.height * (scale - 1f) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            failed -> {
                Text(
                    text = "Could not render page ${pageIndex + 1}",
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            bitmap == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }

            else -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

/**
 * Thread-safe wrapper around PdfRenderer. PdfRenderer itself is not safe for concurrent page
 * rendering, so renderPage is synchronized.
 */
class PdfRenderSession private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    val pageCount: Int
        get() = renderer.pageCount

    @Synchronized
    fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap? {
        if (pageIndex !in 0 until renderer.pageCount) return null
        renderer.openPage(pageIndex).use { page ->
            val safeWidth = targetWidth.coerceIn(320, DEFAULT_RENDER_WIDTH)
            val baseScale = safeWidth.toFloat() / page.width.toFloat().coerceAtLeast(1f)
            var width = safeWidth
            var height = (page.height * baseScale).toInt().coerceAtLeast(1)

            val pixels = width.toLong() * height.toLong()
            if (pixels > MAX_RENDER_PIXELS) {
                val reduction = kotlin.math.sqrt(MAX_RENDER_PIXELS.toDouble() / pixels.toDouble())
                width = (width * reduction).toInt().coerceAtLeast(1)
                height = (height * reduction).toInt().coerceAtLeast(1)
            }

            return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfRenderSession {
            val descriptor = when (uri.scheme) {
                "file" -> {
                    val path = requireNotNull(uri.path) { "Missing PDF path" }
                    ParcelFileDescriptor.open(
                        File(path),
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                }
                else -> context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("Unable to open PDF")
            }

            return try {
                PdfRenderSession(descriptor, PdfRenderer(descriptor))
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }
    }
}
