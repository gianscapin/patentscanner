# 🚀 PaddleOCR - Inicio Rápido

## Pasos para activar PaddleOCR

### 1️⃣ Instalar NDK en Android Studio

```bash
# Abrir Android Studio → Tools → SDK Manager → SDK Tools
# Marcar:
- ✅ NDK (Side by side) versión 25.1.8937393
- ✅ CMake versión 3.22.1

# Click en OK
```

### 2️⃣ Descargar Modelos y Librerías

```bash
cd /Users/gianfrancoscapin/Downloads/Soflex/patentscanner

# Ejecutar script de descarga (puede tardar 2-3 minutos)
./download_paddleocr_assets.sh
```

**Si el script falla:**
- Verificar conexión a internet
- Descargar manualmente desde: https://github.com/PaddlePaddle/Paddle-Lite/releases/tag/v2.10

### 3️⃣ Sincronizar y Compilar

```bash
# En Android Studio
File → Sync Project with Gradle Files

# O desde terminal
./gradlew clean
./gradlew assembleDebug
```

### 4️⃣ Inicializar PaddleOCR en MainActivity

Agrega esto en `MainActivity.kt`:

```kotlin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val paddleOCR = PaddleOCRPredictor.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar PaddleOCR en background
        lifecycleScope.launch(Dispatchers.IO) {
            val success = paddleOCR.init(applicationContext)
            withContext(Dispatchers.Main) {
                if (success) {
                    Log.d("MainActivity", "✅ PaddleOCR listo")
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            LectorPatenteTheme {
                LicensePlateReaderApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        paddleOCR.release()
    }
}
```

### 5️⃣ Cambiar de ML Kit a PaddleOCR

En `CameraPreview.kt`, busca esta línea (~línea 80):

```kotlin
// ANTES
val analyzer = TextRecognitionAnalyzer(...)

// DESPUÉS
val analyzer = PaddleOCRAnalyzer(...)
```

### 6️⃣ Ejecutar la App

```bash
# Conectar dispositivo Android
# Ejecutar en Android Studio o:
./gradlew installDebug
```

---

## ✅ Verificación

### Verificar archivos descargados:

```bash
# Modelos
ls -lh app/src/main/assets/models/
# Debe mostrar:
# - ch_PP-OCRv3_det_infer.nb (~8 MB)
# - ch_PP-OCRv3_rec_infer.nb (~8 MB)
# - ch_ppocr_mobile_v2.0_cls_infer.nb (~2 MB)
# - ppocr_keys_v1.txt (~7 KB)

# Librerías nativas
ls -lh app/src/main/jniLibs/arm64-v8a/
# Debe mostrar:
# - libpaddle_light_api_shared.so (~3 MB)
```

### Verificar logcat:

```bash
adb logcat -s PaddleOCRPredictor:D PaddleOCR-JNI:D

# Deberías ver:
# D/PaddleOCRPredictor: Inicializando PaddleOCR con 4 hilos...
# D/PaddleOCR-JNI: Inicializando PaddleOCR...
# D/PaddleOCR-JNI: PaddleOCR inicializado exitosamente
# D/PaddleOCRPredictor: ✅ PaddleOCR inicializado exitosamente
```

---

## ⚠️ Problemas Comunes

| Error | Solución |
|-------|----------|
| "Library not found" | Instalar NDK desde SDK Manager |
| "Modelo no encontrado" | Re-ejecutar `./download_paddleocr_assets.sh` |
| "Initialization failed" | Verificar archivos en `app/src/main/assets/models/` |
| APK muy grande | Normal, PaddleOCR agrega ~20 MB |

---

## 📖 Documentación Completa

Ver: `PADDLEOCR_INTEGRACION.md` para detalles técnicos completos.

---

**NOTA:** La primera vez que ejecutes la app, PaddleOCR tardará ~1 segundo en inicializar (copia modelos de assets a almacenamiento interno). Las siguientes veces será instantáneo.