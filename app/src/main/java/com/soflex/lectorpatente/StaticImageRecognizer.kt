package com.soflex.lectorpatente

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Utilidad para reconocer texto en imágenes estáticas
 * Útil para tests y procesamiento de imágenes guardadas
 */
object StaticImageRecognizer {
    private const val TAG = "StaticImageRecognizer"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Procesa una imagen desde un archivo
     */
    suspend fun processImageFile(
        file: File,
        applyPreprocessing: Boolean = true,
        preprocessMode: ImageProcessor.PreprocessMode = ImageProcessor.PreprocessMode.BALANCED
    ): RecognitionResult = suspendCoroutine { continuation ->
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                continuation.resumeWithException(Exception("No se pudo cargar la imagen: ${file.absolutePath}"))
                return@suspendCoroutine
            }

            processImageBitmap(bitmap, applyPreprocessing, preprocessMode, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando archivo de imagen", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Procesa una imagen desde assets
     */
    suspend fun processImageFromAssets(
        context: Context,
        assetPath: String,
        applyPreprocessing: Boolean = true,
        preprocessMode: ImageProcessor.PreprocessMode = ImageProcessor.PreprocessMode.BALANCED
    ): RecognitionResult = suspendCoroutine { continuation ->
        try {
            val inputStream: InputStream = context.assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                continuation.resumeWithException(Exception("No se pudo cargar la imagen desde assets: $assetPath"))
                return@suspendCoroutine
            }

            Log.d(TAG, "Procesando imagen desde assets: $assetPath")
            processImageBitmap(bitmap, applyPreprocessing, preprocessMode, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando imagen desde assets", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Procesa una imagen desde un URI
     */
    suspend fun processImageFromUri(
        context: Context,
        uri: Uri,
        applyPreprocessing: Boolean = true,
        preprocessMode: ImageProcessor.PreprocessMode = ImageProcessor.PreprocessMode.BALANCED
    ): RecognitionResult = suspendCoroutine { continuation ->
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                continuation.resumeWithException(Exception("No se pudo cargar la imagen desde URI: $uri"))
                return@suspendCoroutine
            }

            processImageBitmap(bitmap, applyPreprocessing, preprocessMode, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando imagen desde URI", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Procesa un Bitmap directamente
     */
    suspend fun processImageBitmap(
        bitmap: Bitmap,
        applyPreprocessing: Boolean = true,
        preprocessMode: ImageProcessor.PreprocessMode = ImageProcessor.PreprocessMode.BALANCED
    ): RecognitionResult = suspendCoroutine { continuation ->
        processImageBitmap(bitmap, applyPreprocessing, preprocessMode, continuation)
    }

    /**
     * Implementación interna de procesamiento de Bitmap
     */
    private fun processImageBitmap(
        bitmap: Bitmap,
        applyPreprocessing: Boolean,
        preprocessMode: ImageProcessor.PreprocessMode,
        continuation: kotlin.coroutines.Continuation<RecognitionResult>
    ) {
        try {
            Log.d(TAG, "Iniciando procesamiento de imagen - Tamaño: ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "Preprocesamiento activado: $applyPreprocessing, Modo: $preprocessMode")

            val processedBitmap = if (applyPreprocessing) {
                Log.d(TAG, "Aplicando preprocesamiento en modo $preprocessMode...")
                ImageProcessor.preprocessForOCR(bitmap, preprocessMode)
            } else {
                bitmap
            }

            val image = InputImage.fromBitmap(processedBitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    Log.d(TAG, "═══════════════════════════════════")
                    Log.d(TAG, "Texto detectado (crudo):")
                    Log.d(TAG, rawText)
                    Log.d(TAG, "═══════════════════════════════════")

                    val plates = LicensePlateValidator.extractLicensePlates(rawText)

                    val result = RecognitionResult(
                        rawText = rawText,
                        detectedPlates = plates,
                        imageSize = "${processedBitmap.width}x${processedBitmap.height}",
                        preprocessingApplied = applyPreprocessing,
                        preprocessMode = preprocessMode.name
                    )

                    Log.d(TAG, "Patentes encontradas: ${plates.size}")
                    plates.forEach { Log.d(TAG, "  → $it") }

                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error en reconocimiento de texto", e)
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando bitmap", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Resultado del reconocimiento de imagen
     */
    data class RecognitionResult(
        val rawText: String,
        val detectedPlates: List<String>,
        val imageSize: String,
        val preprocessingApplied: Boolean,
        val preprocessMode: String = "BALANCED"
    )
}