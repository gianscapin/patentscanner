package com.soflex.lectorpatente

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    enum class PreprocessMode {
        GENTLE,      // Menos procesamiento, más conservador
        BALANCED,    // Balance entre mejora y preservación
        AGGRESSIVE   // Máximo procesamiento para casos difíciles
    }

    /**
     * Procesa una imagen para mejorar el reconocimiento OCR
     * @param bitmap Imagen original
     * @param mode Modo de preprocesamiento (default: BALANCED)
     * @return Imagen procesada optimizada para OCR
     */
    fun preprocessForOCR(bitmap: Bitmap, mode: PreprocessMode = PreprocessMode.BALANCED): Bitmap {
        Log.d(TAG, "Iniciando preprocesamiento - Tamaño original: ${bitmap.width}x${bitmap.height}, Modo: $mode")

        var processed = bitmap

        when (mode) {
            PreprocessMode.GENTLE -> {
                // Procesamiento mínimo - solo mejoras básicas
                processed = scaleToOptimalSize(processed)
                processed = adjustContrast(processed, 1.2f)
                processed = sharpen(processed)
            }
            PreprocessMode.BALANCED -> {
                // Balance entre mejora y preservación de información
                processed = scaleToOptimalSize(processed)
                processed = toGrayscale(processed)
                processed = reduceReflectionsGentle(processed)
                processed = adjustContrast(processed, 1.6f)  // Mayor contraste para destacar caracteres pequeños
                processed = sharpenStrong(processed)  // Usar sharpen fuerte para detectar caracteres pequeños
                processed = autoBrightness(processed)
                processed = enhanceSmallText(processed)  // Nueva función para mejorar texto pequeño
            }
            PreprocessMode.AGGRESSIVE -> {
                // Máximo procesamiento para casos difíciles
                processed = scaleToOptimalSize(processed)
                processed = toGrayscale(processed)
                processed = reduceReflections(processed)
                processed = medianFilter(processed)
                processed = adjustContrast(processed, 1.6f)
                processed = sharpenStrong(processed)
                processed = autoBrightness(processed)
            }
        }

        Log.d(TAG, "Preprocesamiento completado - Tamaño final: ${processed.width}x${processed.height}")

        return processed
    }

    /**
     * Versión simplificada para compatibilidad (usa modo BALANCED)
     */
    fun preprocessForOCR(bitmap: Bitmap): Bitmap {
        return preprocessForOCR(bitmap, PreprocessMode.BALANCED)
    }


    /**
     * Escala la imagen a un tamaño óptimo para OCR
     * Para caracteres pequeños, necesitamos mayor resolución
     */
    private fun scaleToOptimalSize(bitmap: Bitmap): Bitmap {
        // Aumentar resolución para detectar mejor caracteres pequeños como la D
        val targetWidth = 2560
        val targetHeight = 1440

        // Si la imagen ya es grande, no reducir demasiado
        if (bitmap.width >= targetWidth * 0.8 && bitmap.height >= targetHeight * 0.8) {
            return bitmap
        }

        // Si la imagen es muy pequeña, ampliarla
        if (bitmap.width < targetWidth && bitmap.height < targetHeight) {
            val scale = min(
                targetWidth.toFloat() / bitmap.width,
                targetHeight.toFloat() / bitmap.height
            )

            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()

            Log.d(TAG, "Ampliando imagen de ${bitmap.width}x${bitmap.height} a ${newWidth}x${newHeight}")
            return bitmap.scale(newWidth, newHeight, filter = true)
        }

        // Si es muy grande, reducir
        val scale = min(
            targetWidth.toFloat() / bitmap.width,
            targetHeight.toFloat() / bitmap.height
        )

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        Log.d(TAG, "Escalando imagen de ${bitmap.width}x${bitmap.height} a ${newWidth}x${newHeight}")

        return bitmap.scale(newWidth, newHeight, filter = true)
    }

    /**
     * Ajusta el contraste de la imagen
     * @param contrast Factor de contraste (1.0 = sin cambios, >1.0 = más contraste)
     */
    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val cm = ColorMatrix()

        val scale = contrast
        val translate = (-.5f * scale + .5f) * 255f

        cm.set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Mejora la nitidez de la imagen
     */
    private fun sharpen(bitmap: Bitmap): Bitmap {
        // Kernel de nitidez (sharpening kernel)
        val sharpenMatrix = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )

        return applyConvolution(bitmap, sharpenMatrix, 3)
    }

    /**
     * Mejora la nitidez de forma más agresiva
     */
    private fun sharpenStrong(bitmap: Bitmap): Bitmap {
        // Kernel de nitidez más agresivo para mejorar detección de texto
        val sharpenMatrix = floatArrayOf(
            -1f, -1f, -1f,
            -1f, 9f, -1f,
            -1f, -1f, -1f
        )

        return applyConvolution(bitmap, sharpenMatrix, 3)
    }

    /**
     * Ajusta el brillo automáticamente basado en la luminosidad promedio
     */
    private fun autoBrightness(bitmap: Bitmap): Bitmap {
        // Calcular brillo promedio
        var totalBrightness = 0L
        var pixelCount = 0

        // Muestrear cada 10 píxeles para mejor performance
        for (y in 0 until bitmap.height step 10) {
            for (x in 0 until bitmap.width step 10) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (r + g + b) / 3
                pixelCount++
            }
        }

        val avgBrightness = totalBrightness / pixelCount
        Log.d(TAG, "Brillo promedio: $avgBrightness")

        // Si está muy oscuro (< 100), aumentar brillo
        if (avgBrightness < 100) {
            val brightnessFactor = 1.3f
            val cm = ColorMatrix(floatArrayOf(
                brightnessFactor, 0f, 0f, 0f, 0f,
                0f, brightnessFactor, 0f, 0f, 0f,
                0f, 0f, brightnessFactor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))

            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }

            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            Log.d(TAG, "Brillo ajustado con factor: $brightnessFactor")
            return result
        }

        return bitmap
    }

    /**
     * Aplica una convolución a la imagen
     */
    private fun applyConvolution(bitmap: Bitmap, kernel: FloatArray, kernelSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val newPixels = IntArray(width * height)
        val offset = kernelSize / 2

        for (y in offset until height - offset) {
            for (x in offset until width - offset) {
                var r = 0f
                var g = 0f
                var b = 0f

                for (ky in 0 until kernelSize) {
                    for (kx in 0 until kernelSize) {
                        val pixelY = y + ky - offset
                        val pixelX = x + kx - offset
                        val pixel = pixels[pixelY * width + pixelX]
                        val kernelValue = kernel[ky * kernelSize + kx]

                        r += ((pixel shr 16) and 0xFF) * kernelValue
                        g += ((pixel shr 8) and 0xFF) * kernelValue
                        b += (pixel and 0xFF) * kernelValue
                    }
                }

                val newR = max(0, min(255, r.toInt()))
                val newG = max(0, min(255, g.toInt()))
                val newB = max(0, min(255, b.toInt()))

                newPixels[y * width + x] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }

        result.setPixels(newPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Convierte a escala de grises (útil para debug y algunos casos)
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val cm = ColorMatrix().apply {
            setSaturation(0f)
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Reduce reflejos y normaliza la iluminación
     * Útil para placas de patentes con superficies reflectivas
     */
    private fun reduceReflections(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calcular brillo promedio por regiones para normalización adaptativa
        val blockSize = 32
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Calcular brillo del píxel
                val brightness = (r + g + b) / 3

                // Si el brillo es muy alto (reflejo), reducirlo
                val adjustedBrightness = if (brightness > 200) {
                    // Reducir píxeles muy brillantes (reflejos)
                    val factor = 0.7f
                    val newR = (r * factor).toInt().coerceIn(0, 255)
                    val newG = (g * factor).toInt().coerceIn(0, 255)
                    val newB = (b * factor).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
                } else {
                    pixel
                }

                pixels[y * width + x] = adjustedBrightness
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "Reflejos reducidos")
        return result
    }

    /**
     * Versión más suave de reducción de reflejos
     * Preserva más información original
     */
    private fun reduceReflectionsGentle(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Calcular brillo del píxel
                val brightness = (r + g + b) / 3

                // Solo reducir reflejos muy fuertes
                val adjustedPixel = if (brightness > 230) {
                    val factor = 0.85f
                    val newR = (r * factor).toInt().coerceIn(0, 255)
                    val newG = (g * factor).toInt().coerceIn(0, 255)
                    val newB = (b * factor).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
                } else {
                    pixel
                }

                pixels[y * width + x] = adjustedPixel
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "Reflejos reducidos suavemente")
        return result
    }

    /**
     * Aplica filtro de mediana para reducir ruido
     * Preserva mejor los bordes que un filtro gaussiano
     */
    private fun medianFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val newPixels = IntArray(width * height)

        val windowSize = 3
        val offset = windowSize / 2

        for (y in offset until height - offset) {
            for (x in offset until width - offset) {
                val rValues = mutableListOf<Int>()
                val gValues = mutableListOf<Int>()
                val bValues = mutableListOf<Int>()

                // Recolectar valores de la ventana
                for (wy in -offset..offset) {
                    for (wx in -offset..offset) {
                        val pixel = pixels[(y + wy) * width + (x + wx)]
                        rValues.add((pixel shr 16) and 0xFF)
                        gValues.add((pixel shr 8) and 0xFF)
                        bValues.add(pixel and 0xFF)
                    }
                }

                // Obtener la mediana
                rValues.sort()
                gValues.sort()
                bValues.sort()
                val medianIndex = rValues.size / 2

                val r = rValues[medianIndex]
                val g = gValues[medianIndex]
                val b = bValues[medianIndex]

                newPixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Copiar bordes sin modificar
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y < offset || y >= height - offset || x < offset || x >= width - offset) {
                    newPixels[y * width + x] = pixels[y * width + x]
                }
            }
        }

        result.setPixels(newPixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "Filtro de mediana aplicado")
        return result
    }

    /**
     * Aplica binarización adaptativa
     * Mejora la detección de texto con iluminación no uniforme
     */
    private fun adaptiveThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val newPixels = IntArray(width * height)

        // Convertir a escala de grises primero
        val grayPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            grayPixels[i] = gray
        }

        // Aplicar threshold adaptativo
        val blockSize = 15
        val offset = blockSize / 2
        val c = 10 // Constante de ajuste

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Calcular promedio local
                var sum = 0
                var count = 0

                for (wy in max(0, y - offset)..min(height - 1, y + offset)) {
                    for (wx in max(0, x - offset)..min(width - 1, x + offset)) {
                        sum += grayPixels[wy * width + wx]
                        count++
                    }
                }

                val localMean = sum / count
                val threshold = localMean - c

                // Aplicar threshold
                val pixelValue = grayPixels[y * width + x]
                val binaryValue = if (pixelValue > threshold) 255 else 0

                newPixels[y * width + x] = (0xFF shl 24) or (binaryValue shl 16) or (binaryValue shl 8) or binaryValue
            }
        }

        result.setPixels(newPixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "Threshold adaptativo aplicado")
        return result
    }

    /**
     * Mejora la detección de texto pequeño aplicando técnicas específicas
     * Útil para caracteres pequeños como la D en patentes duplicadas
     */
    private fun enhanceSmallText(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val newPixels = IntArray(width * height)

        // Aplicar un filtro que realce los bordes de caracteres pequeños
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val centerPixel = pixels[y * width + x]
                val centerBrightness = ((centerPixel shr 16) and 0xFF +
                                       (centerPixel shr 8) and 0xFF +
                                       centerPixel and 0xFF) / 3

                // Calcular gradiente local
                val topBrightness = ((pixels[(y-1) * width + x] shr 16) and 0xFF +
                                    (pixels[(y-1) * width + x] shr 8) and 0xFF +
                                    pixels[(y-1) * width + x] and 0xFF) / 3

                val bottomBrightness = ((pixels[(y+1) * width + x] shr 16) and 0xFF +
                                       (pixels[(y+1) * width + x] shr 8) and 0xFF +
                                       pixels[(y+1) * width + x] and 0xFF) / 3

                val leftBrightness = ((pixels[y * width + (x-1)] shr 16) and 0xFF +
                                     (pixels[y * width + (x-1)] shr 8) and 0xFF +
                                     pixels[y * width + (x-1)] and 0xFF) / 3

                val rightBrightness = ((pixels[y * width + (x+1)] shr 16) and 0xFF +
                                      (pixels[y * width + (x+1)] shr 8) and 0xFF +
                                      pixels[y * width + (x+1)] and 0xFF) / 3

                // Si hay un borde fuerte (diferencia significativa), realzarlo
                val gradient = kotlin.math.abs(centerBrightness - topBrightness) +
                              kotlin.math.abs(centerBrightness - bottomBrightness) +
                              kotlin.math.abs(centerBrightness - leftBrightness) +
                              kotlin.math.abs(centerBrightness - rightBrightness)

                val enhancedValue = if (gradient > 40) {
                    // Realzar bordes (probablemente texto)
                    val factor = 1.3f
                    val r = (((centerPixel shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
                    val g = (((centerPixel shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
                    val b = ((centerPixel and 0xFF) * factor).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    centerPixel
                }

                newPixels[y * width + x] = enhancedValue
            }
        }

        // Copiar bordes sin modificar
        for (y in 0 until height) {
            newPixels[y * width] = pixels[y * width]
            newPixels[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }
        for (x in 0 until width) {
            newPixels[x] = pixels[x]
            newPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }

        result.setPixels(newPixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "Texto pequeño mejorado")
        return result
    }

    /**
     * Rota un bitmap según los grados especificados
     * @param bitmap Imagen a rotar
     * @param rotationDegrees Grados de rotación (0, 90, 180, 270)
     * @return Bitmap rotado
     */
    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }

        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )

        Log.d(TAG, "Bitmap rotado ${rotationDegrees}° - Tamaño: ${rotatedBitmap.width}x${rotatedBitmap.height}")

        return rotatedBitmap
    }
}
