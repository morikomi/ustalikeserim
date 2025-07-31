package com.ersinozdogan.ustalikeserimv.ui.camera

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ersinozdogan.ustalikeserimv.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    var granted by remember { mutableStateOf(false) }
    val boxes      by viewModel.boxes.collectAsState()
    val indexBoxes by viewModel.indexBoxes.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            painter = painterResource(R.drawable.ic_baseline_camera_alt_24),
            contentDescription = "Kamera",
            modifier = Modifier
                .size(64.dp)
                .clickable { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Kamera",
            modifier = Modifier.clickable { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
        )
        Spacer(Modifier.height(16.dp))

        if (granted) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType          = PreviewView.ScaleType.FIT_CENTER
                        }.also { pv ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val provider = cameraProviderFuture.get()
                                val rotation = pv.display.rotation

                                val previewUseCase = Preview.Builder()
                                    .setTargetRotation(rotation)
                                    .build()
                                    .also { it.setSurfaceProvider(pv.surfaceProvider) }

                                val analysisUseCase = ImageAnalysis.Builder()
                                    .setTargetRotation(rotation)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(
                                            ContextCompat.getMainExecutor(ctx),
                                            viewModel.getAnalyzer(pv)
                                        )
                                    }

                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    previewUseCase,
                                    analysisUseCase
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )

                Canvas(Modifier.matchParentSize()) {
                    val stroke = Stroke(width = 2.dp.toPx())

                    // nano224'tan gelen kırmızı kutular
                    boxes.forEach { b ->
                        drawRect(
                            color   = Color.Red,
                            topLeft = Offset(b.left, b.top),
                            size    = androidx.compose.ui.geometry.Size(b.width, b.height),
                            style   = stroke
                        )
                    }

                    // yolov_meter'tan gelen endeks bölgesini döndürerek çiz
                    indexBoxes.forEach { ib ->
                        val cx = ib.left   + ib.width  / 2f
                        val cy = ib.top    + ib.height / 2f
                        val hw = ib.width  / 2f
                        val hh = ib.height / 2f
                        val rad = Math.toRadians(ib.angle.toDouble()).toFloat()
                        val c   = cos(rad)
                        val s   = sin(rad)

                        val path = Path().apply {
                            moveTo(cx + (-hw * c - -hh * s), cy + (-hw * s + -hh * c))
                            lineTo(cx + ( hw * c - -hh * s), cy + ( hw * s + -hh * c))
                            lineTo(cx + ( hw * c -  hh * s), cy + ( hw * s +  hh * c))
                            lineTo(cx + (-hw * c -  hh * s), cy + (-hw * s +  hh * c))
                            close()
                        }
                        drawPath(path, Color.Green, style = stroke)
                    }
                }
            }
        }
    }
}