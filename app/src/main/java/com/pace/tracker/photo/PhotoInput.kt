package com.pace.tracker.photo

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pace.tracker.PaceApp
import kotlinx.coroutines.launch
import java.io.File

/**
 * Two buttons – Camera (in-app CameraX) and Gallery (system photo picker). Calls [onPhoto] with the
 * absolute path of a copy stored in app-private storage.
 */
@Composable
fun PhotoInputButtons(
    onPhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
    cameraLabel: String = "Camera",
    galleryLabel: String = "Gallery",
) {
    val context = LocalContext.current
    val storage = (context.applicationContext as PaceApp).container.photoStorage
    val scope = rememberCoroutineScope()
    var showCamera by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    onPhoto(storage.importUri(uri))
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't import photo", Toast.LENGTH_SHORT).show()
                } finally {
                    busy = false
                }
            }
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { showCamera = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text("  $cameraLabel")
        }
        FilledTonalButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text("  $galleryLabel")
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }

    if (showCamera) {
        CameraCaptureDialog(
            onCaptured = { file ->
                showCamera = false
                busy = true
                scope.launch {
                    try {
                        onPhoto(storage.importCapture(file))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't save photo", Toast.LENGTH_SHORT).show()
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { showCamera = false },
        )
    }
}

/** Full-screen CameraX capture with front/back switch. */
@Composable
fun CameraCaptureDialog(onCaptured: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val storage = (context.applicationContext as PaceApp).container.photoStorage
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (hasPermission) {
                var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
                var capturing by remember { mutableStateOf(false) }
                val previewView = remember { PreviewView(context) }
                val imageCapture = remember {
                    ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                }
                DisposableEffect(lensFacing) {
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Camera unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }, ContextCompat.getMainExecutor(context))
                    onDispose {
                        if (future.isDone) runCatching { future.get().unbindAll() }
                    }
                }
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Filled.Close, "Cancel", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Box(
                        Modifier
                            .size(84.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(8.dp)
                            .background(if (capturing) Color.Gray else Color.White, CircleShape),
                    ) {
                        IconButton(
                            onClick = {
                                if (capturing) return@IconButton
                                capturing = true
                                val file = storage.newCaptureFile()
                                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                                imageCapture.takePicture(
                                    options,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            capturing = false
                                            onCaptured(file)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            capturing = false
                                            Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, "Take photo", tint = Color.Black)
                        }
                    }
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                            else CameraSelector.LENS_FACING_BACK
                        },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Filled.Cameraswitch, "Switch camera", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp).statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (denied) "Camera permission denied. You can still pick photos from the gallery, " +
                            "or enable the camera in system settings." else "Requesting camera permission…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    FilledTonalButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
