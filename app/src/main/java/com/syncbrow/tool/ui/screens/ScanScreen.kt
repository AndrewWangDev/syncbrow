package com.syncbrow.tool.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.syncbrow.tool.R
import java.util.concurrent.Executors

@Composable
fun ScanScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isNavigating by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var lastInvalidScanTime by remember { mutableStateOf(0L) }
    var lastInvalidScannedContent by remember { mutableStateOf("") }
    var nonUrlContent by remember { mutableStateOf<String?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Reset navigating flag when we come back to this screen
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNavigating = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun handleBarcodeResult(url: String?) {
        if (url != null && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))) {
            if (isNavigating) return
            isNavigating = true
            Toast.makeText(context, context.getString(R.string.scan_success_toast), Toast.LENGTH_SHORT).show()
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            navController.navigate("browser?url=$encoded") {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        } else if (!url.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastInvalidScanTime > 2500L || url != lastInvalidScannedContent) {
                lastInvalidScanTime = now
                lastInvalidScannedContent = url
                nonUrlContent = url
            }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val url = barcodes.firstOrNull()?.rawValue
                        handleBarcodeResult(url)
                    }
                    .addOnFailureListener {
                        val msg = context.getString(R.string.scan_failed)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                val msg = context.getString(R.string.scan_failed)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor) { imageProxy ->
                                    processImageProxy(barcodeScanner, imageProxy) { result ->
                                        handleBarcodeResult(result)
                                    }
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            // Handle bind errors
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = size.width * 0.7f
                val left = (size.width - boxSize) / 2
                val top = (size.height - boxSize) / 2
                val rect = Rect(Offset(left, top), Size(boxSize, boxSize))
                val cornerRadius = 16.dp.toPx()

                // Cut out middle
                val path = Path().apply {
                    addRoundRect(RoundRect(rect, CornerRadius(cornerRadius, cornerRadius)))
                }

                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.5f))
                }

                // Draw scan border
                drawRoundRect(
                    color = Color.White,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Bottom Actions: Photo picker & Flashlight
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flashlight toggle
                IconButton(onClick = {
                    val cam = camera
                    if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                        torchEnabled = !torchEnabled
                        cam.cameraControl.enableTorch(torchEnabled)
                        val text = if (torchEnabled) R.string.scan_flashlight_on else R.string.scan_flashlight_off
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = stringResource(R.string.scan_flashlight),
                        tint = if (torchEnabled) Color.Yellow else Color.White
                    )
                }

                // Select image from album
                IconButton(onClick = {
                    pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.scan_select_image),
                        tint = Color.White
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.scan_permission_required))
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.bookmark_save))
                }
            }
        }
    }

    // Modal Dialog for Non-URL Scan Results
    if (nonUrlContent != null) {
        val content = nonUrlContent!!
        AlertDialog(
            onDismissRequest = { nonUrlContent = null },
            title = { Text(stringResource(R.string.scan_result_title)) },
            text = {
                Column {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("scan_result", content))
                        Toast.makeText(context, context.getString(R.string.scan_result_copy), Toast.LENGTH_SHORT).show()
                        nonUrlContent = null
                    }) {
                        Text(stringResource(R.string.scan_result_copy))
                    }
                    TextButton(onClick = {
                        val encoded = java.net.URLEncoder.encode(content, "UTF-8")
                        nonUrlContent = null
                        navController.navigate("browser?url=https://www.google.com/search?q=$encoded") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }) {
                        Text(stringResource(R.string.scan_result_search))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, content)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                    nonUrlContent = null
                }) {
                    Text(stringResource(R.string.scan_result_share))
                }
            }
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Unit
) {
    try {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let {
                            onBarcodeDetected(it)
                        }
                    }
                }
                .addOnCompleteListener {
                    try { imageProxy.close() } catch (_: Exception) {}
                }
        } else {
            imageProxy.close()
        }
    } catch (e: Exception) {
        try { imageProxy.close() } catch (_: Exception) {}
    }
}
