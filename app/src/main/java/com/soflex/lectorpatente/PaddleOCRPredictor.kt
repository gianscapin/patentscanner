package com.soflex.lectorpatente

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File

/**
 * Wrapper de Kotlin para PaddleOCR usando JNI
 *
 * Esta clase proporciona una interfaz simple para usar PaddleOCR en Android
 * a través de Paddle-Lite y código nativo (C++/JNI)
 */
class PaddleOCRPredictor private constructor() {

    companion object {
        private const val TAG = "PaddleOCRPredictor"

        @Volatile
        private var instance: PaddleOCRPredictor? = null

        /**
         * Obtener instancia singleton del predictor
         */
        fun getInstance(): PaddleOCRPredictor {
            return instance ?: synchronized(this) {
                instance ?: PaddleOCRPredictor().also { instance = it }
            }
        }

        // Cargar librería nativa
        init {
            try {
                System.loadLibrary("paddleocr_jni")
                Log.d(TAG, "Librería nativa paddleocr_jni cargada exitosamente")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error cargando librería nativa: ${e.message}", e)
            }
        }
    }

    private var isInitialized = false

    /**
     * Inicializar PaddleOCR con modelos
     *
     * @param context Contexto de Android
     * @param numThreads Número de hilos para inferencia (por defecto 4)
     * @return true si se inicializó correctamente, false en caso contrario
     */
    fun init(context: Context, numThreads: Int = 4): Boolean {
        if (isInitialized) {
            Log.w(TAG, "PaddleOCR ya está inicializado")
            return true
        }

        Log.d(TAG, "Inicializando PaddleOCR con $numThreads hilos...")

        try {
            // Rutas a los modelos en assets
            val modelDir = context.filesDir.absolutePath + "/models"

            // Copiar modelos de assets a almacenamiento interno si no existen
            val modelsExist = copyModelsFromAssets(context, modelDir)
            if (!modelsExist) {
                Log.e(TAG, "No se pudieron copiar los modelos desde assets")
                return false
            }

            val detModelPath = "$modelDir/ch_ppocr_mobile_v2.0_det_slim_opt.nb"
            val recModelPath = "$modelDir/ch_ppocr_mobile_v2.0_rec_slim_opt.nb"
            val clsModelPath = "$modelDir/ch_ppocr_mobile_v2.0_cls_slim_opt.nb"
            val dictPath = "$modelDir/ppocr_keys_v1.txt"

            // Verificar que los archivos existen
            if (!File(detModelPath).exists()) {
                Log.e(TAG, "Modelo de detección no encontrado: $detModelPath")
                return false
            }
            if (!File(recModelPath).exists()) {
                Log.e(TAG, "Modelo de reconocimiento no encontrado: $recModelPath")
                return false
            }
            if (!File(clsModelPath).exists()) {
                Log.e(TAG, "Modelo de clasificación no encontrado: $clsModelPath")
                return false
            }
            if (!File(dictPath).exists()) {
                Log.e(TAG, "Diccionario no encontrado: $dictPath")
                return false
            }

            // Llamar a función nativa para inicializar
            isInitialized = nativeInit(
                detModelPath,
                recModelPath,
                clsModelPath,
                dictPath,
                numThreads
            )

            if (isInitialized) {
                Log.d(TAG, "PaddleOCR inicializado exitosamente")
            } else {
                Log.e(TAG, "Error inicializando PaddleOCR (nativeInit retornó false)")
            }

            return isInitialized

        } catch (e: Exception) {
            Log.e(TAG, "Error durante inicialización: ${e.message}", e)
            return false
        }
    }

    /**
     * Ejecutar OCR en un Bitmap
     *
     * @param bitmap Imagen a procesar
     * @return Texto detectado
     */
    fun runOCR(bitmap: Bitmap): String {
        if (!isInitialized) {
            Log.e(TAG, "PaddleOCR no está inicializado. Llama a init() primero.")
            return ""
        }

        return try {
            nativeRunOCR(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando OCR: ${e.message}", e)
            ""
        }
    }

    /**
     * Liberar recursos
     */
    fun release() {
        if (isInitialized) {
            nativeRelease()
            isInitialized = false
            Log.d(TAG, "Recursos de PaddleOCR liberados")
        }
    }

    /**
     * Copiar modelos desde assets a almacenamiento interno
     */
    private fun copyModelsFromAssets(context: Context, targetDir: String): Boolean {
        try {
            val modelDirFile = File(targetDir)
            if (!modelDirFile.exists()) {
                modelDirFile.mkdirs()
            }

            val modelFiles = listOf(
                "ch_ppocr_mobile_v2.0_det_slim_opt.nb",
                "ch_ppocr_mobile_v2.0_rec_slim_opt.nb",
                "ch_ppocr_mobile_v2.0_cls_slim_opt.nb",
                "ppocr_keys_v1.txt"
            )

            for (fileName in modelFiles) {
                val targetFile = File(targetDir, fileName)

                // Solo copiar si no existe o es diferente
                if (!targetFile.exists()) {
                    Log.d(TAG, "Copiando $fileName desde assets...")

                    context.assets.open("models/$fileName").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d(TAG, "  ✓ $fileName copiado (${targetFile.length() / 1024} KB)")
                }
            }

            Log.d(TAG, "Modelos disponibles en $targetDir")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error copiando modelos: ${e.message}", e)
            return false
        }
    }

    // Métodos nativos (implementados en C++ via JNI)
    private external fun nativeInit(
        detModelPath: String,
        recModelPath: String,
        clsModelPath: String,
        dictPath: String,
        numThreads: Int
    ): Boolean

    private external fun nativeRunOCR(bitmap: Bitmap): String

    private external fun nativeRelease()
}