package com.example.aidocumentscanner.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aidocumentscanner.scanner.DocumentScanner
import com.example.aidocumentscanner.scanner.ImageEnhancer
import com.example.aidocumentscanner.scanner.OpenCVManager
import com.example.aidocumentscanner.util.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class CapturedScanPage(
    val bitmap: Bitmap,
    val autoCropped: Boolean,
    val confidence: Float
)

@Composable
fun CameraScreen(
    onImageCaptured: (Bitmap) -> Unit,
    onMultipleImagesCaptured: (List<Bitmap>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val capturedPages = remember { mutableStateListOf<CapturedScanPage>() }
    var handedOff by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var autoCropEnabled by remember { mutableStateOf(true) }
    var captureFlash by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequested = true
        hasPermission = granted
    }

    fun processIncomingBitmap(source: Bitmap): CapturedScanPage {
        if (!autoCropEnabled || !OpenCVManager.isReady()) {
            return CapturedScanPage(source, false, 0f)
        }
        val crop = DocumentScanner.autoCrop(source)
        if (crop.bitmap !== source && !source.isRecycled) source.recycle()
        return CapturedScanPage(crop.bitmap, crop.wasCropped, crop.confidence)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                uris.mapNotNull { BitmapLoader.decode(context, it) }
            }
            val processed = withContext(Dispatchers.Default) {
                decoded.map(::processIncomingBitmap)
            }
            capturedPages.addAll(processed)
            isImporting = false
            if (processed.isEmpty()) {
                Toast.makeText(
                    context,
                    "Could not read the selected images",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        OpenCVManager.initializeAsync(context) {}
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasPermission, lensFacing) {
        if (!hasPermission) return@LaunchedEffect
        cameraError = null

        try {
            val provider = context.awaitCameraProviderPhase3()
            cameraProvider = provider
            val back = CameraSelector.DEFAULT_BACK_CAMERA
            val front = CameraSelector.DEFAULT_FRONT_CAMERA
            canSwitchCamera = provider.hasCamera(back) && provider.hasCamera(front)

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            if (!provider.hasCamera(selector)) {
                lensFacing = if (provider.hasCamera(back)) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                return@LaunchedEffect
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(
                    previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                )
                .build()

            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                capture
            )
            imageCapture = capture
        } catch (_: Throwable) {
            imageCapture = null
            boundCamera = null
            cameraError = "Unable to start the camera"
        }
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (camera.cameraInfo.hasFlashUnit()) {
            runCatching { camera.cameraControl.enableTorch(torchEnabled) }
        } else {
            torchEnabled = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            runCatching { cameraProvider?.unbindAll() }
            cameraExecutor.shutdown()
            if (!handedOff) {
                capturedPages.forEach { page ->
                    if (!page.bitmap.isRecycled) page.bitmap.recycle()
                }
            }
        }
    }

    fun finishSession() {
        if (capturedPages.isEmpty()) return
        handedOff = true
        val bitmaps = capturedPages.map { it.bitmap }
        if (bitmaps.size == 1) {
            onImageCaptured(bitmaps.first())
        } else {
            onMultipleImagesCaptured(bitmaps)
        }
    }

    fun requestBack() {
        if (capturedPages.isEmpty()) onBack() else showDiscardDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Framing guide. This is deliberately only a guide; it does not pretend
            // to be live edge detection.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .height(420.dp)
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.72f),
                        RoundedCornerShape(16.dp)
                    )
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Camera access is needed only when you choose to scan.",
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
                if (permissionRequested) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }
                    ) {
                        Text("Open app settings")
                    }
                }
            }
        }

        IconButton(
            onClick = ::requestBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }

        if (hasPermission) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = autoCropEnabled,
                    onClick = { autoCropEnabled = !autoCropEnabled },
                    label = { Text("Auto crop") },
                    leadingIcon = {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    }
                )

                if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                    IconButton(
                        onClick = { torchEnabled = !torchEnabled },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            if (torchEnabled) "Flash off" else "Flash on",
                            tint = Color.White
                        )
                    }
                }

                if (canSwitchCamera) {
                    IconButton(
                        onClick = {
                            lensFacing =
                                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Cameraswitch, "Switch camera", tint = Color.White)
                    }
                }
            }
        }

        if (capturedPages.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(capturedPages) { index, page ->
                            Box {
                                Image(
                                    bitmap = page.bitmap.asImageBitmap(),
                                    contentDescription = "Captured page ${index + 1}",
                                    modifier = Modifier
                                        .size(width = 62.dp, height = 82.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            if (page.autoCropped) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White.copy(alpha = 0.45f)
                                            },
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    "${index + 1}",
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            IconButton(
                                onClick = {
                                    capturedPages.lastOrNull()?.let { old ->
                                        val rotated = ImageEnhancer.rotate(old.bitmap, 90f)
                                        capturedPages[capturedPages.lastIndex] =
                                            old.copy(bitmap = rotated)
                                        if (!old.bitmap.isRecycled) old.bitmap.recycle()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.RotateRight,
                                    "Rotate last page",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    capturedPages.removeLastOrNull()?.bitmap?.let {
                                        if (!it.isRecycled) it.recycle()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, "Delete last page", tint = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    capturedPages.removeLastOrNull()?.bitmap?.let {
                                        if (!it.isRecycled) it.recycle()
                                    }
                                    Toast.makeText(
                                        context,
                                        "Last page removed. Capture it again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, "Retake last page", tint = Color.White)
                            }
                        }

                        Button(onClick = ::finishSession) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Done (${capturedPages.size})")
                        }
                    }

                    CaptureControls(
                        isCapturing = isCapturing,
                        isImporting = isImporting,
                        canCapture = hasPermission && imageCapture != null,
                        onImport = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onCapture = {
                            val capture = imageCapture ?: return@CaptureControls
                            if (isCapturing || isImporting) return@CaptureControls
                            isCapturing = true
                            val output = File(
                                context.cacheDir,
                                "capture_${System.currentTimeMillis()}.jpg"
                            )
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(output).build(),
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        val decoded = try {
                                            BitmapLoader.decode(output)
                                        } finally {
                                            output.delete()
                                        }
                                        val processed = decoded?.let(::processIncomingBitmap)
                                        scope.launch {
                                            isCapturing = false
                                            captureFlash = true
                                            processed?.let(capturedPages::add)
                                            if (processed == null) {
                                                Toast.makeText(
                                                    context,
                                                    "Could not process the photo",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        output.delete()
                                        scope.launch {
                                            isCapturing = false
                                            Toast.makeText(
                                                context,
                                                "Capture failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Keep the full page inside the frame",
                        color = Color.White,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    CaptureControls(
                        isCapturing = isCapturing,
                        isImporting = isImporting,
                        canCapture = hasPermission && imageCapture != null,
                        onImport = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onCapture = {
                            val capture = imageCapture ?: return@CaptureControls
                            if (isCapturing || isImporting) return@CaptureControls
                            isCapturing = true
                            val output = File(
                                context.cacheDir,
                                "capture_${System.currentTimeMillis()}.jpg"
                            )
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(output).build(),
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        val decoded = try {
                                            BitmapLoader.decode(output)
                                        } finally {
                                            output.delete()
                                        }
                                        val processed = decoded?.let(::processIncomingBitmap)
                                        scope.launch {
                                            isCapturing = false
                                            captureFlash = true
                                            processed?.let(capturedPages::add)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        output.delete()
                                        scope.launch { isCapturing = false }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }

        if (captureFlash) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.20f))
            )
            LaunchedEffect(captureFlash) {
                kotlinx.coroutines.delay(90)
                captureFlash = false
            }
        }

        cameraError?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard scanned pages?") },
            text = { Text("The pages captured in this scan session have not been saved yet.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        capturedPages.forEach {
                            if (!it.bitmap.isRecycled) it.bitmap.recycle()
                        }
                        capturedPages.clear()
                        showDiscardDialog = false
                        onBack()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep scanning")
                }
            }
        )
    }
}

@Composable
private fun CaptureControls(
    isCapturing: Boolean,
    isImporting: Boolean,
    canCapture: Boolean,
    onImport: () -> Unit,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onImport,
            enabled = !isCapturing && !isImporting,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, "Import images", tint = Color.White)
        }

        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    enabled = canCapture && !isCapturing && !isImporting,
                    onClick = onCapture
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                CircularProgressIndicator(Modifier.size(34.dp))
            } else {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White
                )
            }
        }
    }
}

private suspend fun android.content.Context.awaitCameraProviderPhase3(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    if (continuation.isActive) continuation.resume(future.get())
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }
