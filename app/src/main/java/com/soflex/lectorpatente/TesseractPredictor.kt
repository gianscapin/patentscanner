package com.soflex.lectorpatente

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

/**
 * Wrapper de Kotlin para Tesseract OCR
 *
 * Esta clase proporciona una interfaz simple para usar Tesseract en Android
 * con configuración optimizada para detección de patentes argentinas
 */
class TesseractPredictor private constructor() {

    companion object {
        private const val TAG = "TesseractPredictor"
        private const val TESSERACT_DATA_PATH = "/tessdata/"  // Relativo a filesDir

        @Volatile
        private var instance: TesseractPredictor? = null

        /**
         * Obtener instancia singleton del predictor
         */
        fun getInstance(): TesseractPredictor {
            return instance ?: synchronized(this) {
                instance ?: TesseractPredictor().also { instance = it }
            }
        }
    }

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false

    /**
     * Inicializar Tesseract con idiomas español e inglés
     *
     * @param context Contexto de Android
     * @return true si se inicializó correctamente, false en caso contrario
     */
    fun init(context: Context): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Tesseract ya está inicializado")
            return true
        }

        Log.d(TAG, "Inicializando Tesseract...")

        try {
            // 1. Copiar traineddata desde assets a filesDir/tessdata/
            val dataPath = context.filesDir.absolutePath
            val tessDataDir = File(dataPath, "tessdata")

            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }

            // Copiar archivos traineddata
            val traineddataFiles = listOf("spa.traineddata", "eng.traineddata")
            for (fileName in traineddataFiles) {
                copyAssetFile(context, "tessdata/$fileName", tessDataDir, fileName)
            }

            // 2. Inicializar TessBaseAPI
            tessApi = TessBaseAPI()
            val success = tessApi!!.init(dataPath, "eng") // Solo inglés (más rápido, suficiente para alfanuméricos)

            if (success) {
                // Configuraciones para optimizar reconocimiento de patentes
                // PSM_SINGLE_LINE: Una sola línea de texto (patentes)
                tessApi!!.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT

                // Whitelist: Letras mayúsculas, números y espacios (patentes argentinas)
                tessApi!!.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ")

                // Configuraciones adicionales para mejorar precisión
                tessApi!!.setVariable("tessedit_char_blacklist", "!@#$%^&*()_+-=[]{}|;':,.<>?/~`")
                tessApi!!.setVariable("classify_bln_numeric_mode", "0")

                // Configuraciones adicionales para mejorar reconocimiento
                tessApi!!.setVariable("tessedit_enable_doc_dict", "0")
                tessApi!!.setVariable("tessedit_enable_dict_correction", "0")

                isInitialized = true
                Log.d(TAG, "✅ Tesseract inicializado correctamente (eng only, PSM_SINGLE_LINE)")
            } else {
                Log.e(TAG, "❌ Error inicializando Tesseract (init retornó false)")
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
    @Synchronized
    fun runOCR(bitmap: Bitmap): String {
        if (!isInitialized || tessApi == null) {
            Log.e(TAG, "Tesseract no está inicializado. Llama a init() primero.")
            return ""
        }

        return try {
            tessApi!!.setImage(bitmap)
            val text = tessApi!!.utF8Text ?: ""
            Log.d(TAG, "Tesseract - Longitud del texto: ${text.length}")
            if (text.isNotEmpty()) {
                Log.d(TAG, "Tesseract - Texto detectado: $text")
            } else {
                Log.w(TAG, "Tesseract - ⚠️ No se detectó texto")
            }
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando OCR: ${e.message}", e)
            ""
        }
    }

    /**
     * Liberar recursos
     */
    fun release() {
        if (tessApi != null) {
            tessApi?.recycle()
            tessApi = null
            isInitialized = false
            Log.d(TAG, "Recursos de Tesseract liberados")
        }
    }

    /**
     * Copiar un archivo desde assets a un directorio destino
     */
    private fun copyAssetFile(context: Context, assetPath: String, targetDir: File, fileName: String) {
        val targetFile = File(targetDir, fileName)

        // Solo copiar si no existe
        if (targetFile.exists()) {
            Log.d(TAG, "$fileName ya existe, saltando copia")
            return
        }

        Log.d(TAG, "Copiando $fileName desde assets...")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "  ✓ $fileName copiado (${targetFile.length() / 1024} KB)")
    }
}