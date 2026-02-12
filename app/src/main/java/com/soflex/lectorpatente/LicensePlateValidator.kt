package com.soflex.lectorpatente

import android.util.Log

object LicensePlateValidator {

    private const val TAG = "LicensePlateValidator"

    // Formato antiguo: ABC 123 o ABCD 123 o NZL D 783 o NZLD 783 o NZL D783
    // (3 letras + espacio opcional + D/T/C opcional + espacio opcional + 3 números)
    private val oldFormatRegex = Regex("^[A-Z]{3}\\s?[DTC]?\\s?\\d{3}$")

    // Formato nuevo Mercosur: AB 123 CD o AB 123D CD (2 letras + 3 números + D/T/C opcional + 2 letras)
    private val newFormatRegex = Regex("^[A-Z]{2}\\s?\\d{3}[DTC]?\\s?[A-Z]{2}$")

    /**
     * Valida y extrae patentes del texto detectado
     * @param text Texto detectado por ML Kit
     * @return Lista de patentes válidas encontradas
     */
    fun extractLicensePlates(text: String): List<String> {
        Log.d(TAG, "───────────────────────────────────")
        Log.d(TAG, "Procesando texto...")

        val plates = mutableListOf<String>()

        // Aplicar correcciones contextuales antes de normalizar
        val correctedText = applyContextualCorrections(text.uppercase())
        Log.d(TAG, "Texto con correcciones: '$correctedText'")

        // Normalizar el texto: eliminar caracteres especiales y convertir a mayúsculas
        val normalizedText = correctedText
            .replace(Regex("[^A-Z0-9\\s\\n]"), "")

        Log.d(TAG, "Texto normalizado: '$normalizedText'")
        Log.d(TAG, "───────────────────────────────────")

        // Buscar en líneas individuales
        normalizedText.lines().forEachIndexed { index, line ->
            val cleanLine = line.trim().replace(Regex("\\s+"), " ")
            if (cleanLine.isNotEmpty()) {
                Log.d(TAG, "Línea $index: '$cleanLine'")
                if (isValidLicensePlate(cleanLine)) {
                    val formatted = formatLicensePlate(cleanLine)
                    plates.add(formatted)
                    Log.d(TAG, "  ✓ VÁLIDA: $formatted")
                } else {
                    Log.d(TAG, "  ✗ No válida")
                }
            }
        }

        // Buscar en palabras contiguas (para casos sin espacios o con saltos de línea)
        val words = normalizedText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        Log.d(TAG, "Palabras encontradas: ${words.size}")

        for (i in words.indices) {
            // Revisar palabra individual
            val word = words[i]
            if (word.length >= 6) {
                Log.d(TAG, "Probando palabra: '$word'")
                if (isValidLicensePlate(word)) {
                    val formatted = formatLicensePlate(word)
                    plates.add(formatted)
                    Log.d(TAG, "  ✓ VÁLIDA: $formatted")
                }
            }

            // Revisar combinación de 2-3 palabras contiguas
            if (i < words.size - 1) {
                val combined = words[i] + words[i + 1]
                if (combined.length in 6..8) {
                    Log.d(TAG, "Probando combinación 2: '$combined'")
                    if (isValidLicensePlate(combined)) {
                        val formatted = formatLicensePlate(combined)
                        plates.add(formatted)
                        Log.d(TAG, "  ✓ VÁLIDA: $formatted")
                    }
                }
            }

            if (i < words.size - 2) {
                val combined = words[i] + words[i + 1] + words[i + 2]
                if (combined.length in 6..8) {
                    Log.d(TAG, "Probando combinación 3: '$combined'")
                    if (isValidLicensePlate(combined)) {
                        val formatted = formatLicensePlate(combined)
                        plates.add(formatted)
                        Log.d(TAG, "  ✓ VÁLIDA: $formatted")
                    }
                }
            }
        }

        val result = plates.distinct()
        Log.d(TAG, "───────────────────────────────────")
        Log.d(TAG, "Patentes encontradas: ${result.size}")
        result.forEach { Log.d(TAG, "  → $it") }
        Log.d(TAG, "═══════════════════════════════════")

        return result
    }

    /**
     * Valida si un string es una patente argentina válida
     */
    private fun isValidLicensePlate(text: String): Boolean {
        val cleaned = text.replace(Regex("\\s+"), "")
        val isOldFormat = oldFormatRegex.matches(cleaned)
        val isMercosur = newFormatRegex.matches(cleaned)

        if (isOldFormat || isMercosur) {
            val tipo = if (isOldFormat) "Antiguo" else "Mercosur"
            Log.d(TAG, "    Validación OK - Formato: $tipo, Texto: '$cleaned'")
        }

        return isOldFormat || isMercosur
    }

    /**
     * Formatea la patente con espacios en el formato correcto
     */
    private fun formatLicensePlate(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), "")

        return when {
            // Formato antiguo sin D/T/C: ABC 123
            cleaned.matches(Regex("^[A-Z]{3}\\d{3}$")) -> {
                "${cleaned.substring(0, 3)} ${cleaned.substring(3, 6)}"
            }
            // Formato antiguo con D/T/C: ABCD 123 o NZL D 783 (manteniendo D separada)
            cleaned.matches(Regex("^[A-Z]{3}[DTC]\\d{3}$")) -> {
                "${cleaned.substring(0, 3)} ${cleaned.substring(3, 4)} ${cleaned.substring(4, 7)}"
            }
            // Formato nuevo sin D/T/C: AB 123 CD
            cleaned.matches(Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$")) -> {
                "${cleaned.substring(0, 2)} ${cleaned.substring(2, 5)} ${cleaned.substring(5, 7)}"
            }
            // Formato nuevo con D/T/C: AB 123D CD o AB 123T CD o AB 123C CD
            cleaned.matches(Regex("^[A-Z]{2}\\d{3}[DTC][A-Z]{2}$")) -> {
                "${cleaned.substring(0, 2)} ${cleaned.substring(2, 6)} ${cleaned.substring(6, 8)}"
            }
            else -> cleaned
        }
    }

    /**
     * Aplica correcciones contextuales para errores comunes de OCR
     * Solo corrige caracteres cuando el contexto indica claramente un error
     */
    private fun applyContextualCorrections(text: String): String {
        var corrected = text

        // Patrón 1: Detectar guiones/guiones bajos entre dígitos y letras en formato Mercosur
        // Ejemplo: "AA 089-JP" → "AA 089D JP" o "AA 089_JP" → "AA 089D JP"
        // Patrón: 2 letras + espacio opcional + 3 dígitos + [guión/underscore/pipe] + 2 letras
        corrected = corrected.replace(
            Regex("([A-Z]{2}\\s?\\d{3})[-_|]([A-Z]{2})"),
            "$1D$2"
        )

        // Patrón 2: Detectar cuando falta la D en formato Mercosur pero el patrón es claro
        // Ejemplo: "AA 089 JP" con 2 letras + 3 dígitos + 2 letras podría ser "AA 089D JP"
        // Solo si la primera letra después de los dígitos podría ser D pero se perdió
        // Este patrón es más conservador y solo lo aplicamos si detectamos el patrón exacto

        // Patrón 3: Corregir números mal detectados que claramente deberían ser letras
        // En contexto de patente Mercosur: si después de 3 dígitos hay un número solo, probablemente sea una D
        // Ejemplo: "AA 089 0JP" → "AA 089 DJP" (el 0 aislado probablemente es D)
        corrected = corrected.replace(
            Regex("([A-Z]{2}\\s?\\d{3})\\s?0([A-Z]{2})"),
            "$1D$2"
        )

        // Patrón 4: Espacios extras o faltantes entre componentes
        // Normalizar "AA089DJP" o "AA  089  D  JP" a formato consistente
        corrected = corrected.replace(
            Regex("([A-Z]{2})\\s*(\\d{3})\\s*([DTC]?)\\s*([A-Z]{2})"),
            "$1 $2$3 $4"
        )

        Log.d(TAG, "Correcciones aplicadas: '$text' → '$corrected'")

        return corrected
    }

    /**
     * Obtiene el tipo de patente
     */
    fun getPlateType(plate: String): String {
        val cleaned = plate.replace(Regex("\\s+"), "")
        return when {
            oldFormatRegex.matches(cleaned) -> "Formato Antiguo"
            newFormatRegex.matches(cleaned) -> "Formato Mercosur"
            else -> "Desconocido"
        }
    }
}
