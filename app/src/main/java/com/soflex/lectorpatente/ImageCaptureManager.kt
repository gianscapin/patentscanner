package com.soflex.lectorpatente

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor de captura de imágenes
 * Guarda imágenes en la galería del dispositivo para testing y análisis
 */
object ImageCaptureManager {
    private const val TAG = "ImageCaptureManager"
    private const val FOLDER_NAME = "LectorPatentes"

    /**
     * Guarda un bitmap en la galería
     * @param context Contexto de la aplicación
     * @param bitmap Imagen a guardar
     * @param detectedText Texto detectado (opcional, para nombre de archivo)
     * @return URI de la imagen guardada o null si falló
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        detectedText: String? = null
    ): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = if (detectedText != null && detectedText.isNotBlank()) {
                "patente_${detectedText.replace(" ", "_")}_$timestamp.jpg"
            } else {
                "captura_$timestamp.jpg"
            }

            Log.d(TAG, "Guardando imagen: $filename")

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStoreQ(context, bitmap, filename)
            } else {
                saveToExternalStorage(bitmap, filename)
            }

            if (uri != null) {
                Log.d(TAG, "Imagen guardada exitosamente: $uri")
            } else {
                Log.e(TAG, "Error al guardar imagen")
            }

            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando imagen", e)
            null
        }
    }

    /**
     * Guarda imagen usando MediaStore (Android 10+)
     */
    private fun saveToMediaStoreQ(
        context: Context,
        bitmap: Bitmap,
        filename: String
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return uri?.also {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
        }
    }

    /**
     * Guarda imagen en almacenamiento externo (Android 9 y anteriores)
     */
    private fun saveToExternalStorage(bitmap: Bitmap, filename: String): Uri? {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, FOLDER_NAME)

        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, filename)

        return try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando en almacenamiento externo", e)
            null
        }
    }

    /**
     * Guarda imagen en el directorio interno de la app (no requiere permisos)
     * Útil para debugging y tests
     */
    fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        detectedText: String? = null
    ): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = if (detectedText != null && detectedText.isNotBlank()) {
                "patente_${detectedText.replace(" ", "_")}_$timestamp.jpg"
            } else {
                "captura_$timestamp.jpg"
            }

            val capturesDir = File(context.filesDir, "captures")
            if (!capturesDir.exists()) {
                capturesDir.mkdirs()
            }

            val file = File(capturesDir, filename)

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            Log.d(TAG, "Imagen guardada en almacenamiento interno: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando en almacenamiento interno", e)
            null
        }
    }

    /**
     * Lista todas las capturas guardadas en almacenamiento interno
     */
    fun listInternalCaptures(context: Context): List<File> {
        val capturesDir = File(context.filesDir, "captures")
        return if (capturesDir.exists()) {
            capturesDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Elimina una captura del almacenamiento interno
     */
    fun deleteInternalCapture(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Captura eliminada: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando captura", e)
            false
        }
    }

    /**
     * Elimina todas las capturas del almacenamiento interno
     */
    fun deleteAllInternalCaptures(context: Context): Int {
        val capturesDir = File(context.filesDir, "captures")
        var deletedCount = 0

        if (capturesDir.exists()) {
            capturesDir.listFiles()?.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
        }

        Log.d(TAG, "Eliminadas $deletedCount capturas")
        return deletedCount
    }

    /**
     * Obtiene el tamaño total de capturas en almacenamiento interno
     */
    fun getCapturesSize(context: Context): Long {
        val capturesDir = File(context.filesDir, "captures")
        var totalSize = 0L

        if (capturesDir.exists()) {
            capturesDir.listFiles()?.forEach { file ->
                totalSize += file.length()
            }
        }

        return totalSize
    }

    /**
     * Formatea bytes a formato legible
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
