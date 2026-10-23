package com.txao.authenticator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

/**
 * Live camera QR scanner. The camera permission is requested only when this screen opens, and the
 * preview is never stored or sent anywhere. [onResult] returns true once the code was accepted.
 */
@Composable
fun ScanScreen(onResult: (String) -> Boolean, onClose: () -> Unit, onPermissionFlow: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            onPermissionFlow()
            ask.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (granted) CameraPreview(onResult) else Column(
            Modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (denied) "Camera access was denied. You can still add accounts by entering a setup key or importing a file."
                else "Waiting for camera permission…",
                textAlign = TextAlign.Center, fontSize = 14.sp,
            )
            if (denied) Button(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close scanner") }
        }
        if (granted) {
            Text(
                "Point the camera at the QR code from the service's setup page",
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().glass().padding(16.dp),
                textAlign = TextAlign.Center, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Boolean) {
    val context = LocalContext.current
    val owner = context as LifecycleOwner
    val callback by rememberUpdatedState(onResult)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val reader = QRCodeReader()
            val hints = mapOf(DecodeHintType.TRY_HARDER to true)
            var done = false
            analysis.setAnalyzer(executor) { image: ImageProxy ->
                try {
                    if (!done) {
                        val plane = image.planes[0]
                        val buf = plane.buffer
                        val data = ByteArray(buf.remaining()).also { buf.get(it) }
                        // The Y plane may have padding at the end of each row, hence rowStride as the data width.
                        val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                        val text = try {
                            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
                        } catch (e: Exception) {
                            null
                        } finally {
                            reader.reset()
                        }
                        if (text != null) {
                            previewView.post { if (!done && callback(text)) done = true }
                        }
                    }
                } finally {
                    image.close()
                }
            }
            p.unbindAll()
            p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
