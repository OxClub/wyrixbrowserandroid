package com.wyrix.browser.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.wyrix.browser.BrowserViewModel
import com.wyrix.browser.core.QrContent
import com.wyrix.browser.core.UrlTools
import com.wyrix.browser.ui.browser.copyText
import com.wyrix.browser.ui.common.ScreenScaffold
import com.wyrix.browser.ui.common.WyrixIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private val SCAN_FORMATS = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.CODE_128)

private fun newReader() = MultiFormatReader().apply {
    setHints(mapOf<DecodeHintType, Any>(DecodeHintType.POSSIBLE_FORMATS to SCAN_FORMATS, DecodeHintType.TRY_HARDER to true))
}

private fun decodeFrame(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes[0]
    val buf = plane.buffer
    val data = ByteArray(buf.remaining()); buf.get(data)
    val src = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    return try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text } catch (e: ReaderException) { null } finally { reader.reset() }
}

private suspend fun decodeImage(ctx: android.content.Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val src = RGBLuminanceSource(bmp.width, bmp.height, px)
        val reader = newReader()
        try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text }
        catch (e: ReaderException) { try { reader.reset(); reader.decodeWithState(BinaryBitmap(HybridBinarizer(src.invert()))).text } catch (e2: ReaderException) { null } }
    }.getOrNull()
}

@Composable
fun ScannerScreen(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by vm.scanResult.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var torch by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { val t = decodeImage(ctx, uri); if (t != null) vm.onScanned(t) else vm.toast("No QR code found in that image") }
    }
    ScreenScaffold("Scan QR code", onBack = { vm.dismissScan(); vm.closeScreen() }, actions = {
        if (granted) TextButton(onClick = { torch = !torch }) { Text(if (torch) "Light off" else "Light on") }
        TextButton(onClick = { gallery.launch("image/*") }) { Text("From photo") }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!granted) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
                    Text("WYRIX uses the camera only on this screen, to read QR codes and barcodes on your device. Nothing is uploaded.", Modifier.padding(vertical = 8.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    TextButton(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }) { Text("Open app settings") }
                }
            } else {
                CameraPreview(active = result == null, torch = torch, onDetected = { vm.onScanned(it) })
                Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)))
                Text("Point the camera at a QR code", Modifier.align(Alignment.BottomCenter).padding(24.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(12.dp), color = Color.White)
            }
        }
    }
    result?.let { ScanResultDialog(it, vm) }
}

@Composable
private fun CameraPreview(active: Boolean, torch: Boolean, onDetected: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val activeState = rememberUpdatedState(active)
    val callback = rememberUpdatedState(onDetected)
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val reader = newReader()
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        if (activeState.value) decodeFrame(image, reader)?.let { text -> previewView.post { if (activeState.value) callback.value(text) } }
                    } finally { image.close() }
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { runCatching { future.get().unbindAll() }; camera = null; executor.shutdown() }
    }
    LaunchedEffect(torch, camera) { camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) } }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ScanResultDialog(c: QrContent, vm: BrowserViewModel) {
    val ctx = LocalContext.current
    when (c) {
        is QrContent.Web -> {
            val parts = UrlTools.hostParts(c.raw)
            AlertDialog(
                onDismissRequest = { vm.dismissScan() }, title = { Text("Open this link?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.host, style = MaterialTheme.typography.titleMedium)
                        Text(c.raw.take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (parts?.isPunycode == true) Text("This address uses international characters that can imitate another site. Check it carefully.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (c.raw.startsWith("http://")) Text("Not secure (HTTP).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.openScanned(c.raw) }) { Text("Open") } },
                dismissButton = { Row { TextButton(onClick = { copyText(ctx, c.raw); vm.toast("Link copied"); vm.dismissScan() }) { Text("Copy") }; TextButton(onClick = { vm.dismissScan() }) { Text("Cancel") } } },
            )
        }
        is QrContent.Wifi -> AlertDialog(
            onDismissRequest = { vm.dismissScan() }, title = { Text("Wi-Fi network") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Name: ${c.ssid.ifBlank { "(hidden)" }}"); Text("Security: ${c.security}")
                    Text("WYRIX can't join networks for you. Open Wi-Fi settings and enter these details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS)) }; vm.dismissScan() }) { Text("Wi-Fi settings") } },
            dismissButton = { Row { if (c.password.isNotEmpty()) TextButton(onClick = { vm.copySensitive(ctx, c.password, "Wi-Fi password"); vm.dismissScan() }) { Text("Copy password") }; TextButton(onClick = { vm.dismissScan() }) { Text("Close") } } },
        )
        is QrContent.Contact -> AlertDialog(
            onDismissRequest = { vm.dismissScan() }, title = { Text("Scanned code") }, text = { Text(c.raw.take(400)) },
            confirmButton = { TextButton(onClick = { copyText(ctx, c.raw); vm.toast("Copied"); vm.dismissScan() }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { vm.dismissScan() }) { Text("Close") } },
        )
        is QrContent.Text -> AlertDialog(
            onDismissRequest = { vm.dismissScan() }, title = { Text("Scanned text") }, text = { Text(c.raw.take(600)) },
            confirmButton = { TextButton(onClick = { val t = c.raw; vm.dismissScan(); vm.closeScreen(); vm.submitAddress(t) }) { Text("Search") } },
            dismissButton = { Row { TextButton(onClick = { copyText(ctx, c.raw); vm.toast("Copied"); vm.dismissScan() }) { Text("Copy") }; TextButton(onClick = { vm.dismissScan() }) { Text("Close") } } },
        )
    }
}

/** Shows a QR code for [text] (a page address) that another phone can scan. */
@Composable
fun QrCodeDialog(text: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(text) {
        runCatching {
            if (text.length > 1200) return@runCatching null
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
            val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("QR code for this page") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bmp != null) Image(bmp.asImageBitmap(), "QR code for ${UrlTools.hostOf(text) ?: "this page"}", Modifier.size(240.dp).background(Color.White))
                else Text("This address is too long for a QR code.")
                Text(UrlTools.hostOf(text).orEmpty(), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { copyText(ctx, text); onDismiss() }) { Text("Copy link") } },
    )
}
