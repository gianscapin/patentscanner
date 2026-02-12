package com.soflex.lectorpatente

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class TextRecognitionAnalyzer(
    private val onTextDetected: (String) -> Unit,
    private val enablePreprocessing: Boolean = true,
    private val onImageProcessed: ((Bitmap) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val TAG = "TextRecognition"
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image


        if (mediaImage != null) {
            try {
                // Obtener la rotación de la imagen
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                val bitmap = if (enablePreprocessing) {
                    // Convertir ImageProxy a Bitmap
                    val originalBitmap = imageProxyToBitmap(imageProxy)
                    Log.d(TAG, "Aplicando preprocesamiento de imagen...")
                    // Aplicar preprocesamiento
                    ImageProcessor.preprocessForOCR(originalBitmap)
                } else {
                    imageProxyToBitmap(imageProxy)
                }

                val image = InputImage.fromBitmap(bitmap, rotationDegrees)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val detectedText = visionText.text
                        if (detectedText.isNotEmpty()) {
                            Log.d(TAG, "═══════════════════════════════════")
                            Log.d(TAG, "Texto detectado (crudo):")
                            Log.d(TAG, detectedText)
                            Log.d(TAG, "═══════════════════════════════════")
                            onTextDetected(detectedText)

                            // Rotar el bitmap antes de pasarlo al callback
                            // Esto asegura que las imágenes guardadas tengan la orientación correcta
                            val rotatedBitmap = ImageProcessor.rotateBitmap(bitmap, rotationDegrees)
                            onImageProcessed?.invoke(rotatedBitmap)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error en reconocimiento de texto", e)
                        e.printStackTrace()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando imagen", e)
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Convierte un ImageProxy a Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val nv21Buffer = yuv420ThreePlanesToNV21(
            imageProxy.planes,
            imageProxy.width,
            imageProxy.height
        )

        val yuvImage = YuvImage(
            nv21Buffer,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Convierte YUV_420_888 a NV21
     */
    private fun yuv420ThreePlanesToNV21(
        planes: Array<ImageProxy.PlaneProxy>,
        width: Int,
        height: Int
    ): ByteArray {
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        var rowStride = planes[0].rowStride
        assert(planes[0].pixelStride == 1)

        var pos = 0

        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            var yBufferPos = -rowStride
            while (pos < ySize) {
                yBufferPos += rowStride
                yBuffer.position(yBufferPos)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        rowStride = planes[2].rowStride
        val pixelStride = planes[2].pixelStride

        assert(rowStride == planes[1].rowStride)
        assert(pixelStride == planes[1].pixelStride)

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            val savePixel = vBuffer.get(1)
            vBuffer.get(nv21, ySize, 1)
            vBuffer.position(0)
            vBuffer.get(nv21, ySize + 1, uBuffer.remaining())

            vBuffer.put(1, savePixel)
        } else {
            var vBufferPos = -rowStride
            var uBufferPos = -rowStride
            for (row in 0 until height / 2) {
                vBufferPos += rowStride
                uBufferPos += rowStride
                vBuffer.position(vBufferPos)
                uBuffer.position(uBufferPos)
                for (col in 0 until width / 2) {
                    nv21[pos++] = vBuffer.get()
                    nv21[pos++] = uBuffer.get()
                    if (col < width / 2 - 1) {
                        vBuffer.position(vBuffer.position() + pixelStride - 1)
                        uBuffer.position(uBuffer.position() + pixelStride - 1)
                    }
                }
            }
        }

        return nv21
    }
}
