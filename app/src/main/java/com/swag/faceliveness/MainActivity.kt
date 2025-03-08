package com.swag.faceliveness

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.swag.faceliveness.ui.theme.FaceLivenessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) {
                    finish()
                }
            }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // ✅ Correctly setting Compose UI
        setContent {
            FaceLivenessTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FaceDetectionApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}


@Composable
fun FaceDetectionApp(modifier: Modifier = Modifier) {
    var isRightChecked by remember { mutableStateOf(false) }
    var isLeftChecked by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rotate your head to check the boxes", fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isLeftChecked, onCheckedChange = {})
                Text("Turn Left")
                Spacer(modifier = Modifier.width(32.dp))
                Checkbox(checked = isRightChecked, onCheckedChange = {})
                Text("Turn Right")
            }

            Spacer(modifier = Modifier.height(32.dp))

            CameraPreview { left, right ->
                isLeftChecked = left
                isRightChecked = right
            }
        }
    }
}

@Composable
fun CameraPreview(onHeadTurn: (Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.size(300.dp)
    ) { previewView ->

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                        processImage(imageProxy, onHeadTurn)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(context))
    }
}


@OptIn(ExperimentalGetImage::class)
fun processImage(imageProxy: ImageProxy, onHeadTurn: (Boolean, Boolean) -> Unit) {
    val mediaImage = imageProxy.image ?: return
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .enableTracking()
        .build()

    val detector = FaceDetection.getClient(options)
    detector.process(image)
        .addOnSuccessListener { faces ->
            for (face in faces) {
                val headEulerAngleY = face.headEulerAngleY

                val leftTurn = headEulerAngleY < -15
                val rightTurn = headEulerAngleY > 15

                onHeadTurn(leftTurn, rightTurn)
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}
