package cl.smt.conductores.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

data class SmtCameraInfo(
    val id: String,
    val label: String
)

@Composable
fun Camera2BarcodeScanner(
    barcodeFormat: Int,
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val cameras = remember { obtenerCamarasDisponiblesSmt(context) }
    val cameraIndex = remember { mutableStateOf(0) }
    val loading = remember { mutableStateOf(false) }

    val camera = cameras.getOrNull(cameraIndex.value)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Text("Permiso de cámara no concedido", color = Color.White)
            return@Box
        }

        if (loading.value) {
            CircularProgressIndicator(color = Color(0xFF00C853))
        } else {
            Camera2PreviewInterno(
                cameraId = camera?.id,
                barcodeFormat = barcodeFormat,
                onCodeScanned = onCodeScanned,
                onError = onError
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun Camera2PreviewInterno(
    cameraId: String?,
    barcodeFormat: Int,
    onCodeScanned: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    val scanner = remember(barcodeFormat) {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(barcodeFormat)
                .build()
        )
    }

    val isRunning = remember(cameraId, barcodeFormat) { AtomicBoolean(false) }
    val alreadyScanned = remember(cameraId, barcodeFormat) { AtomicBoolean(false) }
    val processing = remember(cameraId, barcodeFormat) { AtomicBoolean(false) }

    DisposableEffect(cameraId, barcodeFormat) {
        onDispose {
            isRunning.set(false)
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val textureView = TextureView(ctx)
            val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val selectedCameraId = cameraId ?: obtenerCamaraTraseraPrincipal(cameraManager)

            val handlerThread = HandlerThread("SMT-Camera2-$selectedCameraId")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)

            var cameraDevice: CameraDevice? = null
            var captureSession: CameraCaptureSession? = null
            var imageReader: ImageReader? = null

            fun cerrarTodo() {
                isRunning.set(false)

                try { captureSession?.stopRepeating() } catch (_: Exception) {}
                try { captureSession?.close() } catch (_: Exception) {}
                try { cameraDevice?.close() } catch (_: Exception) {}
                try { imageReader?.close() } catch (_: Exception) {}
                try { handlerThread.quitSafely() } catch (_: Exception) {}

                captureSession = null
                cameraDevice = null
                imageReader = null
            }

            fun iniciarCamara(surfaceTexture: SurfaceTexture) {
                try {
                    isRunning.set(true)

                    val size = if (barcodeFormat == Barcode.FORMAT_PDF417) {
                        Size(1920, 1080)
                    } else {
                        Size(1280, 720)
                    }

                    surfaceTexture.setDefaultBufferSize(size.width, size.height)

                    val previewSurface = Surface(surfaceTexture)

                    imageReader = ImageReader.newInstance(
                        size.width,
                        size.height,
                        ImageFormat.YUV_420_888,
                        4
                    )

                    imageReader?.setOnImageAvailableListener({ reader ->
                        val image = try {
                            reader.acquireLatestImage()
                        } catch (_: Exception) {
                            null
                        } ?: return@setOnImageAvailableListener

                        if (!isRunning.get() || alreadyScanned.get() || processing.get()) {
                            image.close()
                            return@setOnImageAvailableListener
                        }

                        processing.set(true)

                        try {
                            val inputImage = InputImage.fromMediaImage(image, 90)

                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    val raw = barcodes.firstOrNull()?.rawValue

                                    if (!raw.isNullOrBlank() && alreadyScanned.compareAndSet(false, true)) {
                                        isRunning.set(false)

                                        try { captureSession?.stopRepeating() } catch (_: Exception) {}

                                        onCodeScanned(raw)
                                    }
                                }
                                .addOnFailureListener {
                                    if (isRunning.get()) {
                                        onError("No se pudo leer el código")
                                    }
                                }
                                .addOnCompleteListener {
                                    try { image.close() } catch (_: Exception) {}
                                    processing.set(false)
                                }
                        } catch (_: Exception) {
                            try { image.close() } catch (_: Exception) {}
                            processing.set(false)
                        }
                    }, handler)

                    cameraManager.openCamera(
                        selectedCameraId,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                if (!isRunning.get()) {
                                    camera.close()
                                    return
                                }

                                cameraDevice = camera

                                try {
                                    val analysisSurface = imageReader?.surface ?: return

                                    camera.createCaptureSession(
                                        listOf(previewSurface, analysisSurface),
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                if (!isRunning.get()) {
                                                    session.close()
                                                    return
                                                }

                                                captureSession = session

                                                try {
                                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                                        addTarget(previewSurface)
                                                        addTarget(analysisSurface)

                                                        set(
                                                            CaptureRequest.CONTROL_AF_MODE,
                                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                                        )

                                                        set(
                                                            CaptureRequest.CONTROL_AE_MODE,
                                                            CaptureRequest.CONTROL_AE_MODE_ON
                                                        )
                                                    }

                                                    session.setRepeatingRequest(
                                                        request.build(),
                                                        null,
                                                        handler
                                                    )
                                                } catch (_: Exception) {
                                                    if (isRunning.get()) {
                                                        onError("Error al iniciar preview")
                                                    }
                                                }
                                            }

                                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                                if (isRunning.get()) {
                                                    onError("No se pudo configurar la cámara")
                                                }
                                            }
                                        },
                                        handler
                                    )
                                } catch (_: Exception) {
                                    if (isRunning.get()) {
                                        onError("Error al iniciar cámara")
                                    }
                                }
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                cerrarTodo()
                            }

                            override fun onError(camera: CameraDevice, error: Int) {
                                cerrarTodo()
                                onError("Error de cámara: $error")
                            }
                        },
                        handler
                    )

                    textureView.post {
                        ajustarPreviewTextureViewSmt(
                            textureView,
                            size.width,
                            size.height
                        )
                    }
                } catch (_: Exception) {
                    cerrarTodo()
                    onError("No se pudo abrir cámara $selectedCameraId")
                }
            }

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    iniciarCamara(surface)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    ajustarPreviewTextureViewSmt(textureView, width, height)
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    cerrarTodo()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }

            textureView
        }
    )
}

fun obtenerCamarasDisponiblesSmt(context: Context): List<SmtCameraInfo> {
    return try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraManager.cameraIdList.map { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)

            val tipo = when (facing) {
                CameraCharacteristics.LENS_FACING_BACK -> "Trasera"
                CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externa"
                else -> "Desconocida"
            }

            val focales = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.joinToString(", ") { "${it}mm" }
                ?: "sin focal"

            SmtCameraInfo(
                id = id,
                label = "$tipo ID $id · $focales"
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun obtenerCamaraTraseraPrincipal(cameraManager: CameraManager): String {
    val traseras = cameraManager.cameraIdList.filter { id ->
        val chars = cameraManager.getCameraCharacteristics(id)
        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

    return traseras.firstOrNull() ?: cameraManager.cameraIdList.first()
}

fun ajustarPreviewTextureViewSmt(
    textureView: TextureView,
    previewWidth: Int,
    previewHeight: Int
) {
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()

    if (viewWidth <= 0f || viewHeight <= 0f) return

    val matrix = Matrix()

    val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
    val bufferRect = RectF(0f, 0f, previewHeight.toFloat(), previewWidth.toFloat())

    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    bufferRect.offset(
        centerX - bufferRect.centerX(),
        centerY - bufferRect.centerY()
    )

    matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)

    val scale = maxOf(
        viewHeight / previewHeight.toFloat(),
        viewWidth / previewWidth.toFloat()
    )

    matrix.postScale(scale, scale, centerX, centerY)
    matrix.postRotate(0f, centerX, centerY)

    textureView.setTransform(matrix)
}