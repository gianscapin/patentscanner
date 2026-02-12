package com.soflex.lectorpatente

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturesGalleryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var captures by remember { mutableStateOf(ImageCaptureManager.listInternalCaptures(context)) }
    val totalSize = remember(captures) {
        captures.sumOf { it.length() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                TopAppBar(
                    title = {
                        Column {
                            Text("Capturas Guardadas")
                            Text(
                                text = "${captures.size} imágenes - ${ImageCaptureManager.formatFileSize(totalSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cerrar")
                        }
                    },
                    actions = {
                        if (captures.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    ImageCaptureManager.deleteAllInternalCaptures(context)
                                    captures = emptyList()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Borrar todo")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                // Content
                if (captures.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No hay capturas guardadas",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Usa el botón de cámara para capturar imágenes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(captures) { file ->
                            CaptureItem(
                                file = file,
                                onDelete = {
                                    ImageCaptureManager.deleteInternalCapture(file)
                                    captures = ImageCaptureManager.listInternalCaptures(context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AnalysisResults(
    val balanced: StaticImageRecognizer.RecognitionResult,
    val aggressive: StaticImageRecognizer.RecognitionResult,
    val gentle: StaticImageRecognizer.RecognitionResult
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureItem(
    file: File,
    onDelete: () -> Unit
) {
    var showImage by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResults by remember { mutableStateOf<AnalysisResults?>(null) }
    var showResults by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showImage = true },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileDate(file.lastModified()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = ImageCaptureManager.formatFileSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Diálogo para mostrar la imagen completa
    if (showImage) {
        Dialog(
            onDismissRequest = { showImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    TopAppBar(
                        title = { Text(file.name, style = MaterialTheme.typography.bodyMedium) },
                        navigationIcon = {
                            IconButton(onClick = { showImage = false }) {
                                Icon(Icons.Default.Close, "Cerrar")
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isAnalyzing = true
                                        try {
                                            // Ejecutar los 3 modos de preprocesamiento
                                            val balancedResult = StaticImageRecognizer.processImageFile(
                                                file,
                                                applyPreprocessing = true,
                                                preprocessMode = ImageProcessor.PreprocessMode.BALANCED
                                            )

                                            val aggressiveResult = StaticImageRecognizer.processImageFile(
                                                file,
                                                applyPreprocessing = true,
                                                preprocessMode = ImageProcessor.PreprocessMode.AGGRESSIVE
                                            )

                                            val gentleResult = StaticImageRecognizer.processImageFile(
                                                file,
                                                applyPreprocessing = true,
                                                preprocessMode = ImageProcessor.PreprocessMode.GENTLE
                                            )

                                            analysisResults = AnalysisResults(
                                                balanced = balancedResult,
                                                aggressive = aggressiveResult,
                                                gentle = gentleResult
                                            )

                                            isAnalyzing = false
                                            showResults = true

                                        } catch (e: Exception) {
                                            isAnalyzing = false
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Error al analizar: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isAnalyzing
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analizando...")
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = "Analizar")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Analizar")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = remember {
                            BitmapFactory.decodeFile(file.absolutePath)
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("Error cargando imagen")
                        }
                    }
                }
            }
        }
    }

    // Diálogo para mostrar resultados del análisis
    if (showResults) {
        analysisResults?.let { results ->
            Dialog(
                onDismissRequest = { showResults = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        TopAppBar(
                            title = { Text("Resultados del Análisis") },
                            navigationIcon = {
                                IconButton(onClick = { showResults = false }) {
                                    Icon(Icons.Default.Close, "Cerrar")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Modo BALANCED
                            item {
                                AnalysisResultCard(
                                    modeName = "BALANCED",
                                    result = results.balanced,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                )
                            }

                            // Modo AGGRESSIVE
                            item {
                                AnalysisResultCard(
                                    modeName = "AGGRESSIVE",
                                    result = results.aggressive,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                )
                            }

                            // Modo GENTLE
                            item {
                                AnalysisResultCard(
                                    modeName = "GENTLE",
                                    result = results.gentle,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalysisResultCard(
    modeName: String,
    result: StaticImageRecognizer.RecognitionResult,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Título del modo
            Text(
                text = "Modo: $modeName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Patentes detectadas
            if (result.detectedPlates.isNotEmpty()) {
                Text(
                    text = "✓ Patentes Detectadas:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                result.detectedPlates.forEach { plate ->
                    Text(
                        text = "  • $plate",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                Text(
                    text = "✗ No se detectaron patentes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Texto detectado
            Text(
                text = "Texto detectado:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = if (result.rawText.isNotEmpty()) {
                        result.rawText.take(200)
                    } else {
                        "(Sin texto detectado)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Info adicional
            Text(
                text = "Tamaño: ${result.imageSize}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatFileDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return format.format(date)
}
