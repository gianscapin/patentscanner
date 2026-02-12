#!/bin/bash

# Script para descargar archivos traineddata de Tesseract 4.0
# Tesseract4Android usa el formato "best" traineddata

TESSDATA_DIR="app/src/main/assets/tessdata"
TESSDATA_URL="https://github.com/tesseract-ocr/tessdata_best/raw/main"

# Crear directorio si no existe
mkdir -p "$TESSDATA_DIR"

echo "📥 Descargando traineddata de Tesseract..."

# Descargar español (spa)
if [ ! -f "$TESSDATA_DIR/spa.traineddata" ]; then
    echo "Descargando spa.traineddata..."
    curl -L "$TESSDATA_URL/spa.traineddata" -o "$TESSDATA_DIR/spa.traineddata"
    echo "✅ spa.traineddata descargado ($(du -h "$TESSDATA_DIR/spa.traineddata" | cut -f1))"
else
    echo "⏭️  spa.traineddata ya existe"
fi

# Descargar inglés (eng)
if [ ! -f "$TESSDATA_DIR/eng.traineddata" ]; then
    echo "Descargando eng.traineddata..."
    curl -L "$TESSDATA_URL/eng.traineddata" -o "$TESSDATA_DIR/eng.traineddata"
    echo "✅ eng.traineddata descargado ($(du -h "$TESSDATA_DIR/eng.traineddata" | cut -f1))"
else
    echo "⏭️  eng.traineddata ya existe"
fi

echo ""
echo "🎉 Descarga completada!"
echo "Archivos en: $TESSDATA_DIR"
ls -lh "$TESSDATA_DIR"
