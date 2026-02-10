package com.soflex.lectorpatente

object LicensePlateValidator {

    // Formato antiguo: ABC 123 o ABC123 (3 letras + 3 números)
    private val oldFormatRegex = Regex("^[A-Z]{3}\\s?\\d{3}$")

    // Formato nuevo Mercosur: AB 123 CD o AB123CD (2 letras + 3 números + 2 letras)
    private val newFormatRegex = Regex("^[A-Z]{2}\\s?\\d{3}\\s?[A-Z]{2}$")

    /**
     * Valida y extrae patentes del texto detectado
     * @param text Texto detectado por ML Kit
     * @return Lista de patentes válidas encontradas
     */
    fun extractLicensePlates(text: String): List<String> {
        val plates = mutableListOf<String>()

        // Normalizar el texto: eliminar caracteres especiales y convertir a mayúsculas
        val normalizedText = text
            .uppercase()
            .replace(Regex("[^A-Z0-9\\s\\n]"), "")

        // Buscar en líneas individuales
        normalizedText.lines().forEach { line ->
            val cleanLine = line.trim().replace(Regex("\\s+"), " ")
            if (isValidLicensePlate(cleanLine)) {
                plates.add(formatLicensePlate(cleanLine))
            }
        }

        // Buscar en palabras contiguas (para casos sin espacios o con saltos de línea)
        val words = normalizedText.split(Regex("\\s+"))
        for (i in words.indices) {
            // Revisar palabra individual
            val word = words[i]
            if (isValidLicensePlate(word)) {
                plates.add(formatLicensePlate(word))
            }

            // Revisar combinación de 2-3 palabras contiguas
            if (i < words.size - 1) {
                val combined = words[i] + words[i + 1]
                if (isValidLicensePlate(combined)) {
                    plates.add(formatLicensePlate(combined))
                }
            }

            if (i < words.size - 2) {
                val combined = words[i] + words[i + 1] + words[i + 2]
                if (isValidLicensePlate(combined)) {
                    plates.add(formatLicensePlate(combined))
                }
            }
        }

        return plates.distinct()
    }

    /**
     * Valida si un string es una patente argentina válida
     */
    private fun isValidLicensePlate(text: String): Boolean {
        val cleaned = text.replace(Regex("\\s+"), "")
        return oldFormatRegex.matches(cleaned) || newFormatRegex.matches(cleaned)
    }

    /**
     * Formatea la patente con espacios en el formato correcto
     */
    private fun formatLicensePlate(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), "")

        return when {
            // Formato antiguo: ABC 123
            cleaned.matches(Regex("^[A-Z]{3}\\d{3}$")) -> {
                "${cleaned.substring(0, 3)} ${cleaned.substring(3, 6)}"
            }
            // Formato nuevo: AB 123 CD
            cleaned.matches(Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$")) -> {
                "${cleaned.substring(0, 2)} ${cleaned.substring(2, 5)} ${cleaned.substring(5, 7)}"
            }
            else -> cleaned
        }
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
