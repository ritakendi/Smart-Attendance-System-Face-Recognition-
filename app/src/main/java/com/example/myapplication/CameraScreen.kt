package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

@Composable
internal fun CameraScreen(
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    var faceCount by remember { mutableStateOf(0) }
    var landmarkCount by remember { mutableStateOf(0) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )
    }

    LaunchedEffect(lifecycleOwner, hasPermission) {
        if (hasPermission) {
            controller.setImageAnalysisAnalyzer(analyzerExecutor) { imageProxy ->
                val image = imageProxy.image
                if (image == null) {
                    imageProxy.close()
                } else {
                    detector.process(
                        InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                    )
                        .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { faces ->
                            faceCount = faces.size
                            landmarkCount = faces.sumOf { face -> face.allLandmarks.size }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            detector.close()
            analyzerExecutor.shutdown()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (hasPermission) {
            Text("Faces detected: $faceCount")
            Text("Facial landmarks found: $landmarkCount")
            AndroidView(
                factory = {
                    PreviewView(it).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.controller = controller
                        doOnLayout { controller.bindToLifecycle(lifecycleOwner) }
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Text("Allow camera access to scan faces.")
            Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera")
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}
