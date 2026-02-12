# Imágenes de Test para Patentes

Este directorio contiene imágenes de ejemplo de patentes argentinas para testing.

## Cómo agregar imágenes

1. Tomar fotos de patentes argentinas (reales o de ejemplo)
2. Guardar las imágenes en este directorio con nombres descriptivos

## Formatos de patentes a probar:

### Formato Antiguo:
- `ABC 123` - Formato estándar (3 letras + 3 números)
- `ABCD 123` - Duplicado (3 letras + D + 3 números)
- `ABCT 456` - Triplicado (3 letras + T + 3 números)
- `ABCC 789` - Cuadruplicado (3 letras + C + 3 números)

### Formato Mercosur:
- `AB 123 CD` - Formato estándar (2 letras + 3 números + 2 letras)
- `AB 123D CD` - Duplicado (2 letras + 3 números + D + 2 letras)
- `AB 456T XY` - Triplicado (2 letras + 3 números + T + 2 letras)
- `AB 789C ZZ` - Cuadruplicado (2 letras + 3 números + C + 2 letras)

## Nombres de archivo sugeridos:

```
antigua_abc123.jpg
antigua_abcd123.jpg
mercosur_ab123cd.jpg
mercosur_ab123dcd.jpg
patente_angulo.jpg
patente_baja_luz.jpg
patente_sucio.jpg
```

## Condiciones a probar:

- ✓ Buena iluminación
- ✓ Baja iluminación
- ✓ Ángulo inclinado
- ✓ Patente sucia o con reflejos
- ✓ Diferentes distancias

## Uso en tests:

Las imágenes se pueden usar en tests unitarios con:

```kotlin
@Test
fun testImageRecognition() = runBlocking {
    val result = StaticImageRecognizer.processImageFromAssets(
        context,
        "test_images/mercosur_ab123cd.jpg",
        applyPreprocessing = true
    )
    assertTrue(result.detectedPlates.contains("AB 123 CD"))
}
```

## Notas:

- Las imágenes deben estar en formato JPG o PNG
- Resolución recomendada: 1280x720 o mayor
- Evitar imágenes excesivamente grandes (>5MB)