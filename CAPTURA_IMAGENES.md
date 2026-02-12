# Sistema de Captura de Imágenes

Documentación completa del sistema de captura de imágenes para testing y análisis de reconocimiento de patentes.

---

## Descripción General

El sistema de captura permite guardar las imágenes procesadas por la cámara para:
- **Testing**: Crear un dataset de imágenes reales
- **Análisis**: Revisar qué ve la cámara exactamente
- **Debugging**: Identificar por qué algunas patentes no se reconocen
- **Mejoras**: Ajustar preprocesamiento basado en casos reales

---

## Funcionalidades

### 1. **Captura Manual** 📸
Botón de cámara flotante (esquina inferior derecha)

**Cómo usar:**
1. Apuntar la cámara a una patente
2. Tocar el botón de cámara (icono 📸)
3. La imagen se guarda automáticamente

**Resultado:**
- Imagen guardada en almacenamiento interno
- Notificación de "Captura guardada (N)"
- Contador incrementado

### 2. **Captura Automática** 🔄
Switch "Auto" en esquina superior derecha

**Cómo usar:**
1. Activar el switch "Auto"
2. Cuando se detecte una patente nueva, se captura automáticamente
3. Desactivar cuando no se necesite

**Ventajas:**
- Captura automática de todas las patentes detectadas
- Útil para crear dataset rápidamente
- No requiere intervención manual

### 3. **Galería de Capturas** 🖼️
Botón de galería flotante (encima del botón de cámara)

**Cómo usar:**
1. Tocar el botón de galería (icono 🖼️)
2. Ver lista de todas las capturas guardadas
3. Tocar una captura para verla en grande
4. Tocar icono de papelera para eliminar una captura
5. Usar "Borrar todo" para eliminar todas

**Información mostrada:**
- Nombre del archivo
- Fecha y hora de captura
- Tamaño del archivo
- Total de imágenes y espacio usado

---

## Almacenamiento

### Ubicación de las Imágenes

**Almacenamiento Interno (Predeterminado):**
```
/data/data/com.soflex.lectorpatente/files/captures/
```

**Ventajas:**
- ✓ No requiere permisos adicionales
- ✓ Acceso rápido desde la app
- ✓ Privado (solo la app puede acceder)
- ✓ Se borra al desinstalar la app

**Desventajas:**
- ✗ No accesible desde galería del sistema
- ✗ Requiere exportar para usar fuera de la app

### Formato de Nombres de Archivo

#### Con patente detectada:
```
patente_AB_123D_CD_20260210_143052.jpg
```

#### Sin patente detectada:
```
captura_20260210_143052.jpg
```

**Formato de timestamp:**
- `YYYYMMDD_HHMMSS`
- Ejemplo: `20260210_143052` = 10 de febrero de 2026, 14:30:52

---

## Gestión de Capturas

### Ver Capturas
1. Tocar botón de galería (🖼️)
2. Ver lista ordenada por fecha (más recientes primero)
3. Tocar para ver imagen completa

### Eliminar Capturas

**Individual:**
1. Abrir galería
2. Tocar icono de papelera en la captura deseada
3. La captura se elimina inmediatamente

**Todas:**
1. Abrir galería
2. Tocar "Borrar todo" en la barra superior
3. Todas las capturas se eliminan

### Espacio Utilizado
La galería muestra:
- Número total de capturas
- Espacio total usado (en KB/MB)

**Ejemplo:**
```
12 imágenes - 3.45 MB
```

---

## Uso para Testing

### 1. Crear Dataset de Patentes

```kotlin
// Pasos:
1. Activar "Auto" capture
2. Enfocar varias patentes diferentes
3. Automáticamente se guardan
4. Desactivar "Auto"
5. Revisar capturas en galería
```

### 2. Exportar Capturas para Tests

Las capturas se pueden copiar manualmente desde:
```
/data/data/com.soflex.lectorpatente/files/captures/
```

Hacia:
```
app/src/main/assets/test_images/
```

**Comando adb:**
```bash
# Listar capturas
adb shell ls /data/data/com.soflex.lectorpatente/files/captures/

# Copiar una captura
adb pull /data/data/com.soflex.lectorpatente/files/captures/patente_AB_123_CD_20260210_143052.jpg ./

# Copiar todas las capturas
adb pull /data/data/com.soflex.lectorpatente/files/captures/ ./capturas/
```

### 3. Usar Capturas en Tests

Una vez copiadas a `assets/test_images/`:

```kotlin
@Test
fun testCapturedImage() = runBlocking {
    val result = StaticImageRecognizer.processImageFromAssets(
        context,
        "test_images/patente_AB_123D_CD_20260210_143052.jpg",
        applyPreprocessing = true
    )

    // Verificar resultados
    assertTrue(result.detectedPlates.contains("AB 123D CD"))
}
```

---

## API Programática

### ImageCaptureManager

#### Guardar Bitmap
```kotlin
// En almacenamiento interno (no requiere permisos)
val file = ImageCaptureManager.saveBitmapToInternalStorage(
    context = context,
    bitmap = bitmap,
    detectedText = "AB 123D CD" // Opcional
)

// En galería (requiere permisos)
val uri = ImageCaptureManager.saveBitmapToGallery(
    context = context,
    bitmap = bitmap,
    detectedText = "AB 123D CD" // Opcional
)
```

#### Listar Capturas
```kotlin
val captures: List<File> = ImageCaptureManager.listInternalCaptures(context)
```

#### Eliminar Capturas
```kotlin
// Una captura
val deleted: Boolean = ImageCaptureManager.deleteInternalCapture(file)

// Todas las capturas
val deletedCount: Int = ImageCaptureManager.deleteAllInternalCaptures(context)
```

#### Obtener Espacio Usado
```kotlin
val bytes: Long = ImageCaptureManager.getCapturesSize(context)
val formatted: String = ImageCaptureManager.formatFileSize(bytes) // "3.45 MB"
```

---

## Configuración

### Calidad de Imagen
Las imágenes se guardan con:
- **Formato:** JPEG
- **Calidad:** 95%
- **Tamaño:** Variable (depende de resolución de cámara)

Para cambiar la calidad, editar en `ImageCaptureManager.kt`:
```kotlin
bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
//                                          ^^
//                                    Cambiar este valor (0-100)
```

### Preprocesamiento
Las capturas incluyen el preprocesamiento aplicado:
- ✓ Escalado óptimo
- ✓ Ajuste de contraste
- ✓ Mejora de nitidez
- ✓ Ajuste de brillo

Para capturar imagen sin preprocesamiento, modificar `TextRecognitionAnalyzer`:
```kotlin
TextRecognitionAnalyzer(
    onTextDetected = { ... },
    enablePreprocessing = false // Desactivar preprocesamiento
)
```

---

## Permisos

### Android 10+ (API 29+)
**No se requieren permisos** para almacenamiento interno.

### Android 9 y anteriores (API 28-)
Si se usa `saveBitmapToGallery()`:
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

---

## Troubleshooting

### Problema: No se guardan las capturas

**Soluciones:**
1. Verificar que hay espacio disponible en el dispositivo
2. Revisar logs: `adb logcat -s ImageCaptureManager`
3. Verificar permisos si se usa galería
4. Reiniciar la app

### Problema: No aparecen en la galería del sistema

**Causa:** Las capturas se guardan en almacenamiento interno por defecto.

**Solución:** Para guardar en galería del sistema, usar `saveBitmapToGallery()` y otorgar permisos.

### Problema: Capturas muy grandes

**Soluciones:**
1. Reducir calidad JPEG (de 95 a 85)
2. Escalar imagen antes de guardar
3. Eliminar capturas antiguas regularmente

### Problema: No se pueden exportar las capturas

**Solución:** Usar adb para acceder a almacenamiento interno:
```bash
adb pull /data/data/com.soflex.lectorpatente/files/captures/ ./
```

---

## Casos de Uso

### 1. Debugging de Reconocimiento Fallido
```
1. Apuntar a patente que no se reconoce
2. Capturar manualmente (botón 📸)
3. Abrir galería y ver la imagen
4. Analizar por qué falla (blur, ángulo, luz, etc.)
5. Ajustar parámetros de preprocesamiento
```

### 2. Crear Dataset de Training
```
1. Activar "Auto" capture
2. Enfocar ~50 patentes diferentes
3. Capturar en diferentes condiciones:
   - Buena luz
   - Baja luz
   - Diferentes ángulos
   - Diferentes distancias
4. Exportar con adb
5. Usar en tests unitarios
```

### 3. Comparar Preprocesamiento
```
1. Capturar con preprocesamiento activado
2. Desactivar preprocesamiento en código
3. Capturar la misma patente
4. Comparar ambas imágenes
5. Ver cuál funciona mejor
```

### 4. Reportar Bugs
```
1. Capturar imagen problemática
2. Revisar logs de reconocimiento
3. Exportar captura con adb
4. Incluir en reporte de bug con:
   - Imagen capturada
   - Logs de TextRecognitionAnalyzer
   - Logs de LicensePlateValidator
   - Resultado esperado vs obtenido
```

---

## Mejoras Futuras

### Planificadas:
- [ ] Exportar capturas directamente desde la app
- [ ] Compartir captura por email/WhatsApp
- [ ] Filtros de galería (por fecha, por patente detectada)
- [ ] Comparación lado a lado (preprocesado vs original)
- [ ] Estadísticas de capturas (% reconocido exitosamente)
- [ ] Anotación manual de capturas fallidas
- [ ] Auto-limpieza de capturas antiguas

### En Consideración:
- [ ] Sincronización con cloud storage
- [ ] Batch export de capturas
- [ ] Metadata JSON con resultados de reconocimiento
- [ ] Viewer de capturas con overlay de texto detectado

---

## Ejemplo Completo de Workflow

### Escenario: Mejorar Reconocimiento en Baja Luz

```
Paso 1: Recolectar Datos
  ├─ Activar "Auto" capture
  ├─ Enfocar 10 patentes en baja luz
  └─ Automáticamente se guardan 10 capturas

Paso 2: Analizar Resultados
  ├─ Abrir galería
  ├─ Revisar qué patentes se detectaron
  └─ Identificar patrón (muy oscuras)

Paso 3: Ajustar Preprocesamiento
  ├─ Editar ImageProcessor.kt
  ├─ Aumentar factor de brillo de 1.3 a 1.5
  └─ Rebuild app

Paso 4: Re-probar
  ├─ Exportar capturas con adb
  ├─ Crear tests con esas imágenes
  └─ Verificar mejora en tests

Paso 5: Validar en Producción
  ├─ Capturar nuevas patentes en baja luz
  ├─ Comparar tasa de éxito
  └─ Ajustar parámetros si es necesario
```

---

## Logs Relevantes

### Tag: ImageCaptureManager
```
D/ImageCaptureManager: Guardando imagen: patente_AB_123D_CD_20260210_143052.jpg
D/ImageCaptureManager: Imagen guardada exitosamente: /data/.../captures/patente_AB_123D_CD_20260210_143052.jpg
D/ImageCaptureManager: Eliminadas 12 capturas
```

### Filtrar logs en Logcat:
```bash
adb logcat -s ImageCaptureManager
```

---

**Última actualización:** Febrero 2026
**Versión:** 1.0
**Estado:** Producción