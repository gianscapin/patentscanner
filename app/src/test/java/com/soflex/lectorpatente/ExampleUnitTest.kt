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
}