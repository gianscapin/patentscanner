#!/bin/bash

# Script para descargar modelos PaddleOCR v2 Mobile (más simples y compatibles)
# Estos modelos son para inglés/latín y optimizados para móvil

set -e

MODELS_DIR="app/src/main/assets/models"
TEMP_DIR="temp_paddle_models"

echo "🔍 Descargando modelos PaddleOCR v2 Mobile (inglés/latín)..."

# Crear directorios
mkdir -p "$MODELS_DIR"
mkdir -p "$TEMP_DIR"

# URLs de modelos PaddleOCR v2 Mobile (más compatibles con Paddle-Lite)
DET_URL="https://paddleocr.bj.bcebos.com/dygraph_v2.0/slim/ch_ppocr_mobile_v2.0_det_slim_infer.tar"
REC_URL="https://paddleocr.bj.bcebos.com/dygraph_v2.0/en/en_ppocr_mobile_v2.0_rec_slim_infer.tar"
CLS_URL="https://paddleocr.bj.bcebos.com/dygraph_v2.0/slim/ch_ppocr_mobile_v2.0_cls_slim_infer.tar"

echo ""
echo "📥 Descargando modelo de detección (mobile v2.0)..."
curl -L "$DET_URL" -o "$TEMP_DIR/det.tar"
tar -xf "$TEMP_DIR/det.tar" -C "$TEMP_DIR"

echo ""
echo "📥 Descargando modelo de reconocimiento INGLÉS (mobile v2.0)..."
curl -L "$REC_URL" -o "$TEMP_DIR/rec.tar"
tar -xf "$TEMP_DIR/rec.tar" -C "$TEMP_DIR"

echo ""
echo "📥 Descargando modelo de clasificación (mobile v2.0)..."
curl -L "$CLS_URL" -o "$TEMP_DIR/cls.tar"
tar -xf "$TEMP_DIR/cls.tar" -C "$TEMP_DIR"

echo ""
echo "🔄 Convirtiendo modelos a formato .nb para Paddle-Lite..."
echo ""
echo "⚠️  NOTA: Necesitas usar paddle_lite_opt para convertir los modelos."
echo "    Los modelos descargados están en formato .pdmodel/.pdiparams"
echo "    Necesitas convertirlos a .nb usando paddle_lite_opt"
echo ""
echo "Comandos de conversión (ejecutar en máquina con Paddle-Lite instalado):"
echo ""
echo "# Detección"
echo "paddle_lite_opt --model_file=$TEMP_DIR/ch_ppocr_mobile_v2.0_det_slim_infer/inference.pdmodel \\"
echo "                --param_file=$TEMP_DIR/ch_ppocr_mobile_v2.0_det_slim_infer/inference.pdiparams \\"
echo "                --optimize_out=$MODELS_DIR/en_ppocr_mobile_v2.0_det_slim \\"
echo "                --valid_targets=arm"
echo ""
echo "# Reconocimiento"
echo "paddle_lite_opt --model_file=$TEMP_DIR/en_ppocr_mobile_v2.0_rec_slim_infer/inference.pdmodel \\"
echo "                --param_file=$TEMP_DIR/en_ppocr_mobile_v2.0_rec_slim_infer/inference.pdiparams \\"
echo "                --optimize_out=$MODELS_DIR/en_ppocr_mobile_v2.0_rec_slim \\"
echo "                --valid_targets=arm"
echo ""
echo "# Clasificación"
echo "paddle_lite_opt --model_file=$TEMP_DIR/ch_ppocr_mobile_v2.0_cls_slim_infer/inference.pdmodel \\"
echo "                --param_file=$TEMP_DIR/ch_ppocr_mobile_v2.0_cls_slim_infer/inference.pdiparams \\"
echo "                --optimize_out=$MODELS_DIR/en_ppocr_mobile_v2.0_cls_slim \\"
echo "                --valid_targets=arm"
echo ""
echo "Modelos descargados en: $TEMP_DIR"
echo ""
echo "❌ PROBLEMA: La conversión requiere paddle_lite_opt que no está disponible fácilmente."
echo ""
echo "🔍 Buscando modelos .nb pre-convertidos..."