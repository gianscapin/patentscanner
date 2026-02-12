package com.soflex.lectorpatente

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class OCREngine {
    ML_KIT,
    PADDLE_OCR,
    TESSERACT
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onTextDetected: (String) -> Unit,
    onImageCaptured: ((android.graphics.Bitmap) -> Unit)? = null,
    ocrEngine: OCREngine = OCREngine.ML_KIT  // ML Kit por defecto (PaddleOCR en desarrollo)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(ocrEngine, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                val analyzer = when (ocrEngine) {
                    OCREngine.ML_KIT -> TextRecognitionAnalyzer(
                        onTextDetected = onTextDetected,
                        enablePreprocessing = true,
                        onImageProcessed = onImageCaptured
                    )
                    OCREngine.PADDLE_OCR -> PaddleOCRAnalyzer(
                        onTextDetected = onTextDetected,
                        enablePreprocessing = true,
                        onImageProcessed = onImageCaptured
                    )
                    OCREngine.TESSERACT -> TesseractAnalyzer(
                        onTextDetected = onTextDetected,
                        enablePreprocessing = true,
                        onImageProcessed = onImageCaptured
                    )
                }
                it.setAnalyzer(cameraExecutor, analyzer)
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            setupCameraControl(camera, previewView)
            Log.d("CameraPreview", "Cámara re-configurada para: $ocrEngine")
        } catch (e: Exception) {
            Log.e("CameraPreview", "Error al re-configurar la cámara", e)
        }

        onDispose {
            Log.d("CameraPreview", "Liberando cámara para: $ocrEngine")
            cameraProvider.unbindAll()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )
}

/**
 * Configura el control de cámara para enfoque automático optimizado
 */
private fun setupCameraControl(camera: Camera, previewView: PreviewView) {
    val cameraControl = camera.cameraControl

    // Configurar enfoque continuo
    try {
        val meteringPoint = previewView.meteringPointFactory.createPoint(
            previewView.width / 2f,
            previewView.height / 2f
        )

        val action = FocusMeteringAction.Builder(meteringPoint)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()

        cameraControl.startFocusAndMetering(action)

        // Tap to focus
        previewView.setOnTouchListener { _, event ->
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)
            val focusAction = FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build()
            cameraControl.startFocusAndMetering(focusAction)
            true
        }

        Log.d("CameraPreview", "Enfoque automático configurado")
    } catch (e: Exception) {
        Log.e("CameraPreview", "Error configurando enfoque", e)
    }
}
