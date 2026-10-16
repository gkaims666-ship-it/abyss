package com.abyss.app.ui.feature.camera

import com.abyss.app.ui.i18n.LocalStrings

import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.abyss.app.ui.theme.AbyssPalette
import java.io.File
import java.util.concurrent.Executors

private enum class Mode(val kind: String) {
    Photo("photo"), Video("video"), Circle("circle");
    fun label(S: com.abyss.app.ui.i18n.Strings) = S.mediaKind(kind)
}

/**
 * Camera capture for photos, video and round "circle" clips. On capture the
 * file is handed back via [onCaptured] (path, mime, kind) for AI analysis.
 */
@Composable
fun CameraCaptureScreen(
    palette: AbyssPalette,
    onClose: () -> Unit,
    onCaptured: (path: String, mime: String, kind: String) -> Unit,
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    var mode by remember { mutableStateOf(Mode.Video) }
    var lensBack by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf(false) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    LaunchedEffect(lensBack) {
        val provider = withContextIO { ProcessCameraProvider.getInstance(context).get() }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = CameraSelector.Builder()
            .requireLensFacing(if (lensBack) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT)
            .build()
        val ic = ImageCapture.Builder().build()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD)).build()
        val vc = VideoCapture.withOutput(recorder)
        runCatching {
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(lifecycleOwner, selector, preview, ic, vc) }
                .onFailure { provider.bindToLifecycle(lifecycleOwner, selector, preview, vc) }
        }
        imageCapture = ic
        videoCapture = vc
    }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    fun audioGranted() = ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun takePhoto() {
        val ic = imageCapture ?: return
        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    onCaptured(file.absolutePath, "image/jpeg", "photo")
                }
                override fun onError(e: ImageCaptureException) {}
            })
    }

    fun toggleRecording(kind: String) {
        val vc = videoCapture ?: return
        if (recording) { activeRecording?.stop(); activeRecording = null; return }
        val file = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
        val pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        if (audioGranted()) pending.withAudioEnabled()
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = false
                if (!event.hasError()) onCaptured(file.absolutePath, "video/mp4", kind)
            }
        }
        recording = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Preview — full for photo/video, round-masked for circle.
        if (mode == Mode.Circle) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                AndroidView(factory = { previewView },
                    modifier = Modifier.size(320.dp).clip(CircleShape)
                        .border(3.dp, palette.primary, CircleShape))
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        // Top bar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Close, S.close, tint = Color.White,
                modifier = Modifier.size(32.dp).clickable { activeRecording?.stop(); onClose() })
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Cameraswitch, S.flip, tint = Color.White,
                modifier = Modifier.size(32.dp).clickable { if (!recording) lensBack = !lensBack })
        }

        // Bottom controls
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!recording) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Mode.entries.forEach { m ->
                        val sel = m == mode
                        Box(Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) palette.primary else Color.White.copy(alpha = 0.2f))
                            .clickable { mode = m }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(m.label(S), color = if (sel) palette.onPrimary else Color.White, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.size(20.dp))
            }
            // Shutter
            Box(
                Modifier.size(78.dp).clip(CircleShape)
                    .background(if (recording) palette.error else Color.White)
                    .border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .clickable {
                        when (mode) {
                            Mode.Photo -> takePhoto()
                            Mode.Video -> toggleRecording("video")
                            Mode.Circle -> toggleRecording("circle")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (recording) Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
            }
            Spacer(Modifier.size(8.dp))
            Text(if (recording) S.recordingTapToStop else mode.label(S),
                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
    }
}

private suspend fun <T> withContextIO(block: () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
