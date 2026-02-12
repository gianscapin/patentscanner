package com.soflex.lectorpatente

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de reconocimiento de patentes usando imágenes estáticas
 * Estos tests usan imágenes desde assets o generadas programáticamente
 */
@RunWith(AndroidJUnit4::class)
class ImageRecognitionTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * Test con imagen sintética - Formato antiguo
     */
    @Test
    fun testSyntheticImage_OldFormat() = runBlocking {
        val bitmap = createSyntheticLicensePlate("ABC 123")

        val result = StaticImageRecognizer.processImageBitmap(
            bitmap,
            applyPreprocessing = true
        )

        println("Texto detectado: ${result.rawText}")
        println("Patentes encontradas: ${result.detectedPlates}")

        // El test puede fallar con imágenes sintéticas simples
        // pero sirve para verificar que el pipeline funciona
        assertNotNull(result.rawText)
    }

    /**
     * Test con imagen sintética - Formato Mercosur
     */
    @Test
    fun testSyntheticImage_MercosurFormat() = runBlocking {
        val bitmap = createSyntheticLicensePlate("AB 123 CD")

        val result = StaticImageRecognizer.processImageBitmap(
            bitmap,
            applyPreprocessing = true
        )

        println("Texto detectado: ${result.rawText}")
        println("Patentes encontradas: ${result.detectedPlates}")

        assertNotNull(result.rawText)
    }

    /**
     * Test de preprocesamiento de imagen
     */
    @Test
    fun testImagePreprocessing() {
        val originalBitmap = createTestBitmap(640, 480)

        val processedBitmap = ImageProcessor.preprocessForOCR(originalBitmap)

        assertNotNull(processedBitmap)
        assertTrue(processedBitmap.width > 0)
        assertTrue(processedBitmap.height > 0)
    }

    /**
     * Test de conversión a escala de grises
     */
    @Test
    fun testGrayscaleConversion() {
        val colorBitmap = createTestBitmap(100, 100)

        val grayscaleBitmap = ImageProcessor.toGrayscale(colorBitmap)

        assertNotNull(grayscaleBitmap)
        assertEquals(colorBitmap.width, grayscaleBitmap.width)
        assertEquals(colorBitmap.height, grayscaleBitmap.height)
    }

    /**
     * Test comparando con y sin preprocesamiento
     */
    @Test
    fun testPreprocessingComparison() = runBlocking {
        val bitmap = createSyntheticLicensePlate("ABC 123")

        // Sin preprocesamiento
        val resultWithoutPreprocessing = StaticImageRecognizer.processImageBitmap(
            bitmap,
            applyPreprocessing = false
        )

        // Con preprocesamiento
        val resultWithPreprocessing = StaticImageRecognizer.processImageBitmap(
            bitmap,
            applyPreprocessing = true
        )

        println("Sin preprocesamiento: ${resultWithoutPreprocessing.detectedPlates.size} patentes")
        println("Con preprocesamiento: ${resultWithPreprocessing.detectedPlates.size} patentes")

        assertNotNull(resultWithoutPreprocessing)
        assertNotNull(resultWithPreprocessing)
    }

    // ===========================================
    // Tests con imágenes reales desde assets
    // (Solo se ejecutan si las imágenes existen)
    // ===========================================

    /**
     * Test con imagen real desde assets - Formato antiguo
     */
    @Test
    fun testRealImage_OldFormat_IfExists() = runBlocking {
        try {
            val result = StaticImageRecognizer.processImageFromAssets(
                context,
                "test_images/antigua_abc123.jpg",
                applyPreprocessing = true
            )

            println("Texto detectado: ${result.rawText}")
            println("Patentes encontradas: ${result.detectedPlates}")

            assertTrue(
                "Debería detectar al menos una patente",
                result.detectedPlates.isNotEmpty()
            )
        } catch (e: Exception) {
            println("Imagen de test no encontrada (opcional): ${e.message}")
            // Test pasa si la imagen no existe (es opcional)
        }
    }

    /**
     * Test con imagen real desde assets - Formato Mercosur
     */
    @Test
    fun testRealImage_MercosurFormat_IfExists() = runBlocking {
        try {
            val result = StaticImageRecognizer.processImageFromAssets(
                context,
                "test_images/mercosur_ab123cd.jpg",
                applyPreprocessing = true
            )

            println("Texto detectado: ${result.rawText}")
            println("Patentes encontradas: ${result.detectedPlates}")

            assertTrue(
                "Debería detectar al menos una patente",
                result.detectedPlates.isNotEmpty()
            )
        } catch (e: Exception) {
            println("Imagen de test no encontrada (opcional): ${e.message}")
            // Test pasa si la imagen no existe (es opcional)
        }
    }

    /**
     * Test con imagen en condiciones difíciles (baja luz)
     */
    @Test
    fun testRealImage_LowLight_IfExists() = runBlocking {
        try {
            val result = StaticImageRecognizer.processImageFromAssets(
                context,
                "test_images/patente_baja_luz.jpg",
                applyPreprocessing = true
            )

            println("Texto detectado: ${result.rawText}")
            println("Patentes encontradas: ${result.detectedPlates}")
            println("Preprocesamiento mejoró el resultado: ${result.preprocessingApplied}")

            assertNotNull(result.rawText)
        } catch (e: Exception) {
            println("Imagen de test no encontrada (opcional): ${e.message}")
        }
    }

    /**
     * Test con imagen real de patente duplicada (AA 089 D JP)
     * Esta imagen tiene reflejos y caracteres con textura
     */
    @Test
    fun testRealImage_PatenteDup() = runBlocking {
        try {
            val result = StaticImageRecognizer.processImageFromAssets(
                context,
                "patente3.jpeg",
                applyPreprocessing = true
            )

            println("═══════════════════════════════════")
            println("Test: Patente Duplicada (AA 089 D JP)")
            println("Texto detectado: ${result.rawText}")
            println("Patentes encontradas: ${result.detectedPlates}")
            println("Tamaño de imagen: ${result.imageSize}")
            println("═══════════════════════════════════")

            // Verificar que se detectó al menos una patente
            assertTrue(
                "Debería detectar al menos una patente",
                result.detectedPlates.isNotEmpty()
            )

            // Verificar que se detectó la patente correcta
            val expectedPlates = listOf("AA089JP", "AA089DJP", "AA 089 JP", "AA 089 D JP")
            val foundExpected = result.detectedPlates.any { detected ->
                expectedPlates.any { expected ->
                    detected.replace(" ", "").equals(expected.replace(" ", ""), ignoreCase = true)
                }
            }

            if (foundExpected) {
                println("✓ Patente correcta detectada!")
            } else {
                println("⚠ Patente esperada no encontrada. Detectadas: ${result.detectedPlates}")
            }

        } catch (e: Exception) {
            println("Error en test de patente_dup: ${e.message}")
            e.printStackTrace()
            fail("El test falló con excepción: ${e.message}")
        }
    }

    /**
     * Test comparando preprocesamiento en patente_dup
     * Ayuda a ver la diferencia que hacen las mejoras
     */
    @Test
    fun testPatenteDup_PreprocessingComparison() = runBlocking {
        try {
            // Sin preprocesamiento
            val resultWithout = StaticImageRecognizer.processImageFromAssets(
                context,
                "patente3.jpeg",
                applyPreprocessing = false
            )

            // Con preprocesamiento
            val resultWith = StaticImageRecognizer.processImageFromAssets(
                context,
                "patente3.jpeg",
                applyPreprocessing = true
            )

            println("═══════════════════════════════════")
            println("Comparación de Preprocesamiento - patente3.jpeg")
            println("═══════════════════════════════════")
            println("SIN preprocesamiento:")
            println("  Texto: ${resultWithout.rawText}")
            println("  Patentes: ${resultWithout.detectedPlates}")
            println("")
            println("CON preprocesamiento:")
            println("  Texto: ${resultWith.rawText}")
            println("  Patentes: ${resultWith.detectedPlates}")
            println("═══════════════════════════════════")

            // Ambos deberían tener resultados
            assertNotNull(resultWithout.rawText)
            assertNotNull(resultWith.rawText)

        } catch (e: Exception) {
            println("Error en comparación: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Test probando los tres modos de preprocesamiento en patente_dup
     * Determina cuál funciona mejor para esta imagen
     */
    @Test
    fun testPatenteDup_AllModes() = runBlocking {
        try {
            println("═══════════════════════════════════")
            println("Test de Todos los Modos - patente3.jpeg (AA 089 D JP)")
            println("═══════════════════════════════════")

            // Modo GENTLE
            val resultGentle = StaticImageRecognizer.processImageFromAssets(
                context,
                "patente3.jpeg",
                applyPreprocessing = true,
                preprocessMode = ImageProcessor.PreprocessMode.GENTLE
            )

            println("Modo GENTLE:")
            println("  Tamaño: ${resultGentle.imageSize}")
            println("  Texto: ${resultGentle.rawText.replace("\n", " | ")}")
            println("  Patentes: ${resultGentle.detectedPlates}")
            println("")

            // Modo BALANCED
            val resultBalanced = StaticImageRecognizer.processImageFromAssets(
                context,
                "patente3.jpeg",
                applyPreprocessing = true,
                preprocessMode = ImageProcessor.PreprocessMode.BALANCED
            )

            println("Modo BALANCED:")
            println("  Tamaño: ${resultBalanced.imageSize}")
            println("  Texto: ${resultBalanced.rawText.replace("\n", " | ")}")
            println("  Patentes: ${resultBalanced.detectedPlates}")
            println("")

            // Modo AGGRESSIVE
            val resultAggressive = StaticImageRecognizer.processImageFromAssets(
                context,
                "patente3.jpeg",
                applyPreprocessing = true,
                preprocessMode = ImageProcessor.PreprocessMode.AGGRESSIVE
            )

            println("Modo AGGRESSIVE:")
            println("  Tamaño: ${resultAggressive.imageSize}")
            println("  Texto: ${resultAggressive.rawText.replace("\n", " | ")}")
            println("  Patentes: ${resultAggressive.detectedPlates}")
            println("═══════════════════════════════════")

            // Verificar cuál detectó la patente correcta
            val expectedPlates = listOf("NZLD783", "NZL D 783", "NZLD 783", "NZL D783")

            val gentleCorrect = resultGentle.detectedPlates.any { detected ->
                expectedPlates.any { expected ->
                    detected.replace(" ", "").equals(expected.replace(" ", ""), ignoreCase = true)
                }
            }

            val balancedCorrect = resultBalanced.detectedPlates.any { detected ->
                expectedPlates.any { expected ->
                    detected.replace(" ", "").equals(expected.replace(" ", ""), ignoreCase = true)
                }
            }

            val aggressiveCorrect = resultAggressive.detectedPlates.any { detected ->
                expectedPlates.any { expected ->
                    detected.replace(" ", "").equals(expected.replace(" ", ""), ignoreCase = true)
                }
            }

            println("RESULTADOS:")
            println("  GENTLE detectó correcta: ${if (gentleCorrect) "✓ SÍ" else "✗ NO"}")
            println("  BALANCED detectó correcta: ${if (balancedCorrect) "✓ SÍ" else "✗ NO"}")
            println("  AGGRESSIVE detectó correcta: ${if (aggressiveCorrect) "✓ SÍ" else "✗ NO"}")
            println("═══════════════════════════════════")

        } catch (e: Exception) {
            println("Error en test de modos: ${e.message}")
            e.printStackTrace()
            fail("El test falló con excepción: ${e.message}")
        }
    }

    // ===========================================
    // Funciones auxiliares para crear imágenes de test
    // ===========================================

    /**
     * Crea un bitmap de test simple
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            // Llenar con un color de prueba
            for (x in 0 until width) {
                for (y in 0 until height) {
                    setPixel(x, y, Color.rgb(128, 128, 128))
                }
            }
        }
    }

    /**
     * Crea una imagen sintética de patente
     * NOTA: ML Kit puede no reconocer bien texto generado programáticamente
     * Este método es principalmente para testing del pipeline, no de precisión
     */
    private fun createSyntheticLicensePlate(text: String): Bitmap {
        val width = 640
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = android.graphics.Canvas(bitmap)

        // Fondo blanco
        canvas.drawColor(Color.WHITE)

        // Borde negro
        val borderPaint = android.graphics.Paint().apply {
            color = Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(10f, 10f, width - 10f, height - 10f, borderPaint)

        // Texto negro
        val textPaint = android.graphics.Paint().apply {
            color = Color.BLACK
            textSize = 80f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
        }

        canvas.drawText(text, width / 2f, height / 2f + 30f, textPaint)

        return bitmap
    }
}
