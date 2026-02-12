package com.soflex.lectorpatente

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.soflex.lectorpatente.ui.theme.LectorPatenteTheme

data class DetectedPlate(
    val plate: String,
    val isVerified: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val paddleOCR = PaddleOCRPredictor.getInstance()
    val tesseract = TesseractPredictor.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Estado para controlar la inicialización de PaddleOCR
        val isPaddleOcrInitialized = mutableStateOf(false)

        // Inicializar PaddleOCR en background
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "Inicializando PaddleOCR...")
            val success = paddleOCR.init(applicationContext, numThreads = 4)

            withContext(Dispatchers.Main) {
                if (success) {
                    Log.d("MainActivity", "✅ PaddleOCR inicializado correctamente")
                    isPaddleOcrInitialized.value = true
                } else {
                    Log.e("MainActivity", "❌ Error inicializando PaddleOCR")
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            LectorPatenteTheme {
                // Pasar el estado al composable principal
                LicensePlateReaderApp(isPaddleOcrInitialized = isPaddleOcrInitialized.value)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        paddleOCR.release()
        tesseract.release()
        Log.d("MainActivity", "PaddleOCR y Tesseract liberados")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensePlateReaderApp(isPaddleOcrInitialized: Boolean) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Lector de Patentes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (hasCameraPermission) {
            LicensePlateScanner(
                modifier = Modifier.padding(innerPadding),
                isPaddleOcrInitialized = isPaddleOcrInitialized
            )
        } else {
            PermissionDeniedScreen(
                modifier = Modifier.padding(innerPadding),
                onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

@Composable
fun LicensePlateScanner(modifier: Modifier = Modifier, isPaddleOcrInitialized: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detectedPlates by remember { mutableStateOf<List<DetectedPlate>>(emptyList()) }
    var lastDetectedText by remember { mutableStateOf("Esperando detección...") }
    var shouldCapture by remember { mutableStateOf(false) }
    var lastCapturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showCaptureSuccess by remember { mutableStateOf(false) }
    var captureCount by remember { mutableStateOf(0) }
    var showGalleryDialog by remember { mutableStateOf(false) }

    // Auto-captura cuando se detecta una nueva patente
    var autoCaptureEnabled by remember { mutableStateOf(false) }

    // Motor OCR a usar (ML Kit por defecto, PaddleOCR opcional)
    var ocrEngine by remember { mutableStateOf(OCREngine.ML_KIT) }

    // Inicialización lazy de Tesseract (solo cuando se selecciona)
    var tesseractInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(ocrEngine) {
        if (ocrEngine == OCREngine.TESSERACT && !tesseractInitialized) {
            scope.launch(Dispatchers.IO) {
                Log.d("MainActivity", "Inicializando Tesseract (lazy)...")
                tesseractInitialized = (context as MainActivity).tesseract.init(context)

                withContext(Dispatchers.Main) {
                    if (tesseractInitialized) {
                        Log.d("MainActivity", "✅ Tesseract inicializado correctamente")
                    } else {
                        Log.e("MainActivity", "❌ Error inicializando Tesseract")
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Vista previa de la cámara
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            ocrEngine = ocrEngine,
            onTextDetected = { text ->
                lastDetectedText = text.take(200) // Limitar a 200 caracteres
                val plates = LicensePlateValidator.extractLicensePlates(text)
                if (plates.isNotEmpty()) {
                    val currentPlateStrings = detectedPlates.map { it.plate }
                    val newPlates = plates
                        .filter { it !in currentPlateStrings }
                        .map { DetectedPlate(it, false) }

                    if (newPlates.isNotEmpty()) {
                        detectedPlates = (newPlates + detectedPlates).take(10)

                        // Auto-capturar si está habilitado
                        if (autoCaptureEnabled) {
                            shouldCapture = true
                        }
                    }
                }
            },
            onImageCaptured = { bitmap ->
                if (shouldCapture) {
                    scope.launch {
                        lastCapturedBitmap = bitmap
                        val detectedText = if (detectedPlates.isNotEmpty()) {
                            detectedPlates.first().plate
                        } else {
                            null
                        }

                        // Guardar en almacenamiento interno (no requiere permisos)
                        ImageCaptureManager.saveBitmapToInternalStorage(
                            context,
                            bitmap,
                            detectedText
                        )

                        shouldCapture = false
                        showCaptureSuccess = true
                        captureCount++

                        // Ocultar mensaje después de 2 segundos
                        kotlinx.coroutines.delay(2000)
                        showCaptureSuccess = false
                    }
                }
            }
        )

        // Overlay con guía visual
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Instrucciones en la parte superior
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Apunta la cámara hacia una patente",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Vista de debug con texto detectado
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Debug - Texto detectado:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastDetectedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.heightIn(max = 60.dp)
                        )
                    }
                }
            }

            // Card inferior con patentes y botones
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Sección de patentes detectadas
                    if (detectedPlates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Patentes Detectadas:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = { detectedPlates = emptyList() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Limpiar todo",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(detectedPlates) { detectedPlate ->
                                PlateItem(
                                    detectedPlate = detectedPlate,
                                    onToggleVerified = {
                                        detectedPlates = detectedPlates.map { plate ->
                                            if (plate.plate == detectedPlate.plate) {
                                                plate.copy(isVerified = !plate.isVerified)
                                            } else {
                                                plate
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Botones de acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Botón de captura manual
                        Button(
                            onClick = { shouldCapture = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capturar")
                        }

                        // Botón para ver galería
                        Button(
                            onClick = { showGalleryDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Galería")
                        }
                    }
                }
            }
        }

        // Mensaje de captura exitosa
        if (showCaptureSuccess) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Captura guardada ($captureCount)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Controles en la esquina superior derecha
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Switch para auto-captura
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Auto",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Switch(
                        checked = autoCaptureEnabled,
                        onCheckedChange = { autoCaptureEnabled = it },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            // Selector de motor OCR con 3 opciones
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Motor OCR:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Botón ML Kit
                        Button(
                            onClick = {
                                ocrEngine = OCREngine.ML_KIT
                                detectedPlates = emptyList()
                                lastDetectedText = "Esperando detección..."
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ocrEngine == OCREngine.ML_KIT)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "ML Kit",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Botón PaddleOCR
                        Button(
                            onClick = {
                                ocrEngine = OCREngine.PADDLE_OCR
                                detectedPlates = emptyList()
                                lastDetectedText = "Esperando detección..."
                            },
                            enabled = isPaddleOcrInitialized, // Use the new state here
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ocrEngine == OCREngine.PADDLE_OCR)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Paddle",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Botón Tesseract
                        Button(
                            onClick = {
                                ocrEngine = OCREngine.TESSERACT
                                detectedPlates = emptyList()
                                lastDetectedText = "Esperando detección..."
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ocrEngine == OCREngine.TESSERACT)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Tesseract",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // Diálogo de galería de capturas
        if (showGalleryDialog) {
            CapturesGalleryDialog(
                onDismiss = { showGalleryDialog = false }
            )
        }
    }
}

@Composable
fun PlateItem(
    detectedPlate: DetectedPlate,
    onToggleVerified: () -> Unit
) {
    val plateType = LicensePlateValidator.getPlateType(detectedPlate.plate)
    val backgroundColor = if (detectedPlate.isVerified) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleVerified() },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detectedPlate.plate,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = plateType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            if (detectedPlate.isVerified) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verificado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permiso de Cámara Requerido",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Esta aplicación necesita acceso a la cámara para detectar patentes.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onRequestPermission) {
            Text("Otorgar Permiso")
        }
    }
}