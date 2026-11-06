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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

private const val MATCH_THRESHOLD = 0.5f
private data class RegisteredFace(val student: Student, val embedding: FloatArray)

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
    var knownFacesLoaded by remember { mutableStateOf(false) }
    var attendanceMessage by remember { mutableStateOf("Loading face samples…") }
    val registeredFaces = remember { AtomicReference<List<RegisteredFace>>(emptyList()) }
    val attendanceStatus = remember { ConcurrentHashMap<Int, String>() }
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
        } else {
            val faces = withContext(Dispatchers.IO) {
                val students = AppDatabase.getInstance(context).studentDao()
                    .getAll().filter { it.consentGivenAt != null }.associateBy { it.id }
                AppDatabase.getInstance(context).faceSampleDao().getAll().mapNotNull { sample ->
                    students[sample.studentId]?.let {
                        RegisteredFace(it, sample.embedding.toFloatArray())
                    }
                }
            }
            registeredFaces.set(faces)
            knownFacesLoaded = true
            attendanceMessage = if (faces.isEmpty()) "No face samples registered." else "Ready to scan."
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
                                    if (enrollmentStudentId != null && faces.size != 1) {
                                        latestEmbedding = null
                                    } else if (enrollmentStudentId == null && faces.isEmpty()) {
                                        attendanceMessage = "Show a face to scan."
                                    }
                                }
                                val facesToEmbed = if (enrollmentStudentId == null) faces
                                    else faces.singleOrNull()?.let(::listOf).orEmpty()
                                val now = SystemClock.elapsedRealtime()
                                val canMatch = registeredFaces.get().isNotEmpty()
                                if ((enrollmentStudentId != null || canMatch) && facesToEmbed.isNotEmpty() &&
                                    now - lastEmbeddingTime.get() >= 800L
                                ) {
                                    lastEmbeddingTime.set(now)
                                    for (face in facesToEmbed) {
                                        val crop = imageProxy.faceCrop(face)
                                        if (crop != null) {
                                            val embedding = model.value.embed(crop)
                                            if (enrollmentStudentId != null) {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    latestEmbedding = embedding
                                                }
                                            } else {
                                                val match = registeredFaces.get().bestMatch(embedding)
                                                if (match == null) {
                                                    ContextCompat.getMainExecutor(context).execute {
                                                        attendanceMessage = "No match found; not marked."
                                                    }
                                                } else {
                                                    val message = attendanceStatus[match.student.id]
                                                    if (message != null) {
                                                        ContextCompat.getMainExecutor(context).execute {
                                                            attendanceMessage = message
                                                        }
                                                    } else if (attendanceStatus.putIfAbsent(
                                                            match.student.id, "Marking ${match.student.name}…"
                                                        ) == null
                                                    ) {
                                                        ContextCompat.getMainExecutor(context).execute {
                                                            attendanceMessage = "Marking ${match.student.name}…"
                                                        }
                                                        scope.launch(Dispatchers.IO) {
                                                            val markedAt = System.currentTimeMillis()
                                                            val rowId = try {
                                                                AppDatabase.getInstance(context).attendanceDao().mark(
                                                                    AttendanceRecord(
                                                                        studentId = match.student.id,
                                                                        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                                            .format(Date(markedAt)),
                                                                        markedAt = markedAt
                                                                    )
                                                                )
                                                            } catch (_: Exception) {
                                                                -2L
                                                            }
                                                            val result = when (rowId) {
                                                                -1L -> "Already marked: ${match.student.name}"
                                                                -2L -> "Could not save attendance."
                                                                else -> "Attendance marked: ${match.student.name}"
                                                            }
                                                            if (rowId == -2L) attendanceStatus.remove(match.student.id)
                                                            else attendanceStatus[match.student.id] = result
                                                            ContextCompat.getMainExecutor(context).execute {
                                                                attendanceMessage = result
                                                            }
                                                        }
                                                    }
                                                }
                                            }
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
            } else {
                Text(if (knownFacesLoaded) attendanceMessage else "Loading registered face samples…")
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

private fun ByteArray.toFloatArray(): FloatArray = ByteBuffer.wrap(this)
    .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { FloatArray(size / 4).also(it::get) }

private fun List<RegisteredFace>.bestMatch(embedding: FloatArray): RegisteredFace? {
    var best: RegisteredFace? = null
    var bestScore = MATCH_THRESHOLD
    forEach { candidate ->
        val score = embedding.indices.sumOf {
            (embedding[it] * candidate.embedding[it]).toDouble()
        }.toFloat()
        if (score > bestScore) {
            best = candidate
            bestScore = score
        }
    }
    return best
}
