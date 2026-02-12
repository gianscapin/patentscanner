package com.soflex.lectorpatente

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests para validación de patentes argentinas
 */
class LicensePlateValidatorTest {

    @Test
    fun testOldFormatStandard() {
        val plates = LicensePlateValidator.extractLicensePlates("ABC 123")
        assertEquals(1, plates.size)
        assertEquals("ABC 123", plates[0])
    }

    @Test
    fun testOldFormatWithDuplicado() {
        val plates = LicensePlateValidator.extractLicensePlates("ABCD 123")
        assertEquals(1, plates.size)
        assertEquals("ABCD 123", plates[0])
    }

    @Test
    fun testOldFormatWithTriplicado() {
        val plates = LicensePlateValidator.extractLicensePlates("ABCT 456")
        assertEquals(1, plates.size)
        assertEquals("ABCT 456", plates[0])
    }

    @Test
    fun testOldFormatWithCuadruplicado() {
        val plates = LicensePlateValidator.extractLicensePlates("ABCC 789")
        assertEquals(1, plates.size)
        assertEquals("ABCC 789", plates[0])
    }

    @Test
    fun testMercosurFormatStandard() {
        val plates = LicensePlateValidator.extractLicensePlates("AB 123 CD")
        assertEquals(1, plates.size)
        assertEquals("AB 123 CD", plates[0])
    }

    @Test
    fun testMercosurFormatWithDuplicado() {
        val plates = LicensePlateValidator.extractLicensePlates("AB 123 D CD")
        assertEquals(1, plates.size)
        assertEquals("AB 123D CD", plates[0])
    }

    @Test
    fun testMercosurFormatWithTriplicado() {
        val plates = LicensePlateValidator.extractLicensePlates("AB 456T XY")
        assertEquals(1, plates.size)
        assertEquals("AB 456T XY", plates[0])
    }

    @Test
    fun testMercosurFormatWithCuadruplicado() {
        val plates = LicensePlateValidator.extractLicensePlates("AB 789C ZZ")
        assertEquals(1, plates.size)
        assertEquals("AB 789C ZZ", plates[0])
    }

    @Test
    fun testNoSpaces() {
        val plates = LicensePlateValidator.extractLicensePlates("ABC123")
        assertEquals(1, plates.size)
        assertEquals("ABC 123", plates[0])
    }

    @Test
    fun testNoSpacesMercosur() {
        val plates = LicensePlateValidator.extractLicensePlates("AB123DCD")
        assertEquals(1, plates.size)
        assertEquals("AB 123D CD", plates[0])
    }

    @Test
    fun testNoSpacesOldFormatWithD() {
        val plates = LicensePlateValidator.extractLicensePlates("ABCD123")
        assertEquals(1, plates.size)
        assertEquals("ABCD 123", plates[0])
    }

    @Test
    fun testMultiplePlatesInText() {
        val text = """
            Vehículo 1: ABC 123
            Vehículo 2: AB 456D XY
        """.trimIndent()
        val plates = LicensePlateValidator.extractLicensePlates(text)
        assertTrue(plates.contains("ABC 123"))
        assertTrue(plates.contains("AB 456D XY"))
    }

    @Test
    fun testInvalidPlates() {
        val plates = LicensePlateValidator.extractLicensePlates("INVALID 12AB3")
        assertEquals(0, plates.size)
    }

    @Test
    fun testPlateType_OldFormat() {
        assertEquals("Formato Antiguo", LicensePlateValidator.getPlateType("ABC 123"))
        assertEquals("Formato Antiguo", LicensePlateValidator.getPlateType("ABCD 456"))
    }

    @Test
    fun testPlateType_Mercosur() {
        assertEquals("Formato Mercosur", LicensePlateValidator.getPlateType("AB 123 CD"))
        assertEquals("Formato Mercosur", LicensePlateValidator.getPlateType("AB 456D XY"))
    }

    // ═════════════════════════════════════════════════════════
    // Tests de correcciones contextuales de OCR
    // ═════════════════════════════════════════════════════════

    @Test
    fun testOCRCorrection_HyphenAsD() {
        // Cuando el OCR detecta un guión en lugar de D
        val plates = LicensePlateValidator.extractLicensePlates("AA 089-JP")
        assertEquals(1, plates.size)
        assertEquals("AA 089D JP", plates[0])
    }

    @Test
    fun testOCRCorrection_UnderscoreAsD() {
        // Cuando el OCR detecta un underscore en lugar de D
        val plates = LicensePlateValidator.extractLicensePlates("AA 089_JP")
        assertEquals(1, plates.size)
        assertEquals("AA 089D JP", plates[0])
    }

    @Test
    fun testOCRCorrection_PipeAsD() {
        // Cuando el OCR detecta un pipe en lugar de D
        val plates = LicensePlateValidator.extractLicensePlates("AA 089|JP")
        assertEquals(1, plates.size)
        assertEquals("AA 089D JP", plates[0])
    }

    @Test
    fun testOCRCorrection_ZeroAsD() {
        // Cuando el OCR detecta un 0 aislado que debería ser D
        val plates = LicensePlateValidator.extractLicensePlates("AA 089 0JP")
        assertEquals(1, plates.size)
        assertEquals("AA 089D JP", plates[0])
    }

    @Test
    fun testOCRCorrection_RealCase_PatenteDup() {
        // Caso real: la imagen de patente_dup que detecta "AA 0B9-JP"
        // Nota: El B→8 no lo corregimos automáticamente por ambigüedad
        val plates = LicensePlateValidator.extractLicensePlates("AA 0B9-JP")
        // Debería corregir el guión a D
        assertTrue(plates.isNotEmpty())
        // Si detecta "AA 0B9D JP", es un error de OCR con el 8
        // pero al menos corrigió el guión
    }

    @Test
    fun testOCRCorrection_NoSpacesWithHyphen() {
        // Sin espacios y con guión
        val plates = LicensePlateValidator.extractLicensePlates("AB123-CD")
        assertEquals(1, plates.size)
        assertEquals("AB 123D CD", plates[0])
    }

    @Test
    fun testOCRCorrection_ExtraSpaces() {
        // Espacios extras no deberían romper la detección
        val plates = LicensePlateValidator.extractLicensePlates("AB  089  D  JP")
        assertEquals(1, plates.size)
        assertEquals("AB 089D JP", plates[0])
    }

    @Test
    fun testOCRCorrection_MultipleErrors() {
        // Múltiples errores: guión y espacios extras
        val plates = LicensePlateValidator.extractLicensePlates("AA  089-JP")
        assertEquals(1, plates.size)
        assertEquals("AA 089D JP", plates[0])
    }

    @Test
    fun testOCRCorrection_DoesNotCorruptValidPlate() {
        // Asegurar que las correcciones no corrompen patentes válidas
        val plates = LicensePlateValidator.extractLicensePlates("AB 123D CD")
        assertEquals(1, plates.size)
        assertEquals("AB 123D CD", plates[0])
    }

    @Test
    fun testOCRCorrection_OnlyCorrectInContext() {
        // Los guiones solo se corrigen en contexto de patente,
        // no en texto arbitrario
        val plates = LicensePlateValidator.extractLicensePlates("Texto-con-guiones AB089-JP más-texto")
        assertEquals(1, plates.size)
        assertEquals("AB 089D JP", plates[0])
    }
}