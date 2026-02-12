#!/bin/bash

# Script para descargar modelos y librerías de PaddleOCR para Android
# Ejecutar desde la raíz del proyecto: ./download_paddleocr_assets.sh

set -e  # Salir si hay error

echo "========================================="
echo "Descargando PaddleOCR para Android"
echo "========================================="

# Crear directorios si no existen
mkdir -p app/src/main/jniLibs/armeabi-v7a
mkdir -p app/src/main/jniLibs/arm64-v8a
mkdir -p app/src/main/assets/models
mkdir -p app/src/main/cpp/paddlelite_libs

cd app/src/main/assets/models

# 1. Descargar modelos optimizados (.nb) de PaddleOCR v3
echo ""
echo "📦 Descargando modelos PaddleOCR v3 optimizados..."

# Modelo de detección de texto (ch_PP-OCRv3_det)
echo "  → Modelo de detección..."
curl -L -o ch_PP-OCRv3_det_infer.nb \
  "https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_det_slim_infer.nb" || \
  echo "⚠️  Advertencia: No se pudo descargar el modelo de detección"

# Modelo de reconocimiento de texto (ch_PP-OCRv3_rec)
echo "  → Modelo de reconocimiento..."
curl -L -o ch_PP-OCRv3_rec_infer.nb \
  "https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_slim_infer.nb" || \
  echo "⚠️  Advertencia: No se pudo descargar el modelo de reconocimiento"

# Modelo de clasificación de orientación
echo "  → Modelo de clasificación..."
curl -L -o ch_ppocr_mobile_v2.0_cls_infer.nb \
  "https://paddleocr.bj.bcebos.com/dygraph_v2.0/lite/ch_ppocr_mobile_v2.0_cls_opt.nb" || \
  echo "⚠️  Advertencia: No se pudo descargar el modelo de clasificación"

# Diccionario de caracteres
echo "  → Diccionario de caracteres..."
curl -L -o ppocr_keys_v1.txt \
  "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/ppocr_keys_v1.txt" || \
  echo "⚠️  Advertencia: No se pudo descargar el diccionario"

echo ""
echo "✅ Modelos descargados en app/src/main/assets/models/"
ls -lh

# 2. Descargar Paddle-Lite Android libraries
echo ""
echo "📦 Descargando Paddle-Lite libraries..."

cd ../../../../..  # Volver a raíz del proyecto
cd app/src/main

# Descargar Paddle-Lite v2.10 para Android
PADDLE_LITE_VERSION="2.10"
PADDLE_LITE_URL="https://github.com/PaddlePaddle/Paddle-Lite/releases/download/v${PADDLE_LITE_VERSION}/inference_lite_lib.android.armv8.gcc.c++_shared.with_extra.with_cv.tar.gz"

echo "  → Descargando Paddle-Lite v${PADDLE_LITE_VERSION}..."
curl -L -o paddle_lite.tar.gz "$PADDLE_LITE_URL" || {
  echo "⚠️  Advertencia: Descarga directa falló. Intenta descarga manual desde:"
  echo "     https://github.com/PaddlePaddle/Paddle-Lite/releases"
  exit 1
}

echo "  → Extrayendo archivos..."
tar -xzf paddle_lite.tar.gz
mv inference_lite_lib.android.armv8 cpp/paddlelite_libs/

# Copiar librerías .so a jniLibs
echo "  → Copiando librerías nativas..."
cp cpp/paddlelite_libs/cxx/libs/arm64-v8a/*.so jniLibs/arm64-v8a/
cp cpp/paddlelite_libs/cxx/libs/armeabi-v7a/*.so jniLibs/armeabi-v7a/ 2>/dev/null || echo "⚠️  No hay libs para armeabi-v7a"

# Limpiar archivos temporales
rm paddle_lite.tar.gz

echo ""
echo "✅ Paddle-Lite libraries instaladas"
echo ""
echo "========================================="
echo "✅ Descarga completada exitosamente"
echo "========================================="
echo ""
echo "Archivos descargados:"
echo "  • Modelos (.nb):    app/src/main/assets/models/"
echo "  • Librerías (.so):  app/src/main/jniLibs/"
echo "  • Headers C++:      app/src/main/cpp/paddlelite_libs/"
echo ""
echo "Próximos pasos:"
echo "  1. Verificar que los archivos existen"
echo "  2. Sincronizar el proyecto en Android Studio"
echo "  3. Compilar la aplicación"
echo ""