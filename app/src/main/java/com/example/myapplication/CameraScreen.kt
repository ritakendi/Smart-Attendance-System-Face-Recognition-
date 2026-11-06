package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Composable
internal fun CameraScreen(
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
    enrollmentStudentId: Int? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var latestEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var sampleCount by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val processingExecutor = remember { Executors.newSingleThreadExecutor() }
    val model = remember(context) { lazy { FaceEmbeddingModel(context) } }
    val lastEmbeddingTime = remember { AtomicLong(0L) }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )
    }

    LaunchedEffect(enrollmentStudentId) {
        if (enrollmentStudentId != null) {
            sampleCount = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).faceSampleDao()
                    .getForStudent(enrollmentStudentId).size
            }
        }
    }

    LaunchedEffect(lifecycleOwner, hasPermission, enrollmentStudentId) {
        if (hasPermission) {
            controller.setImageAnalysisAnalyzer(analyzerExecutor) { imageProxy ->
                val image = imageProxy.image
                if (image == null) {
                    imageProxy.close()
                } else {
                    detector.process(InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener(processingExecutor) { faces ->
                            try {
                                ContextCompat.getMainExecutor(context).execute {
                                    faceCount = faces.size
                                    landmarkCount = faces.sumOf { it.allLandmarks.size }
                                    if (faces.size != 1) latestEmbedding = null
                                }
                                val face = faces.singleOrNull()
                                val now = SystemClock.elapsedRealtime()
                                if (enrollmentStudentId != null && face != null &&
                                    now - lastEmbeddingTime.get() >= 800L
                                ) {
                                    lastEmbeddingTime.set(now)
                                    val crop = imageProxy.faceCrop(face)
                                    if (crop != null) {
                                        val embedding = model.value.embed(crop)
                                        ContextCompat.getMainExecutor(context).execute {
                                            latestEmbedding = embedding
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                ContextCompat.getMainExecutor(context).execute {
                                    latestEmbedding = null
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                        .addOnFailureListener(processingExecutor) {
                            imageProxy.close()
                        }
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
            processingExecutor.shutdown()
            if (model.isInitialized()) model.value.close()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (hasPermission) {
            Text("Faces detected: $faceCount")
            Text("Facial landmarks found: $landmarkCount")
            if (enrollmentStudentId != null) {
                Text("Face samples saved: $sampleCount")
                Text(
                    when {
                        faceCount != 1 -> "Show one face to the camera."
                        latestEmbedding == null -> "Preparing face sample…"
                        else -> "Face ready to save."
                    }
                )
                Button(
                    enabled = latestEmbedding != null && !saving,
                    onClick = {
                        val embedding = latestEmbedding ?: return@Button
                        saving = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getInstance(context).faceSampleDao().insert(
                                        FaceSample(studentId = enrollmentStudentId, embedding = embedding.toBytes())
                                    )
                                }
                                sampleCount++
                                latestEmbedding = null
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) "Saving…" else "Save face sample") }
            }
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

private fun ImageProxy.faceCrop(face: Face): Bitmap? {
    val frame = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) frame else Bitmap.createBitmap(
        frame, 0, 0, frame.width, frame.height,
        Matrix().apply { postRotate(rotation.toFloat()) }, true
    )
    val box = face.boundingBox
    val size = (max(box.width(), box.height()) * 1.3f).toInt().coerceAtLeast(1)
    val left = (box.centerX() - size / 2).coerceIn(0, upright.width - 1)
    val top = (box.centerY() - size / 2).coerceIn(0, upright.height - 1)
    val cropSize = minOf(size, upright.width - left, upright.height - top)
    return if (cropSize > 0) Bitmap.createBitmap(upright, left, top, cropSize, cropSize) else null
}

private fun FloatArray.toBytes(): ByteArray = ByteBuffer.allocate(size * 4)
    .order(ByteOrder.LITTLE_ENDIAN).apply { forEach(::putFloat) }.array()
