#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cmath>
#include <android/log.h>
#include <android/bitmap.h>
#include <vector>
#include <memory>
#include <queue>
#include "paddle_api.h"

#define TAG "PaddleOCR-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace paddle::lite_api;

// Estructura para una caja de texto detectada
struct TextBox {
    std::vector<std::pair<int, int>> points;  // 4 puntos de la caja
    float score;
};

// Estructura para almacenar el predictor
struct PaddleOCRContext {
    std::shared_ptr<PaddlePredictor> det_predictor;  // Detección
    std::shared_ptr<PaddlePredictor> rec_predictor;  // Reconocimiento
    std::shared_ptr<PaddlePredictor> cls_predictor;  // Clasificación
    std::vector<std::string> char_dict;               // Diccionario de caracteres
    bool initialized;
};

// Contexto global
static PaddleOCRContext g_context;

// Función auxiliar: Cargar diccionario de caracteres
std::vector<std::string> loadCharDict(const std::string &dict_path) {
    std::vector<std::string> dict;
    std::ifstream file(dict_path);
    if (!file.is_open()) {
        LOGE("No se pudo abrir el diccionario: %s", dict_path.c_str());
        return dict;
    }

    std::string line;
    while (std::getline(file, line)) {
        // Remover caracteres de nueva línea
        if (!line.empty() && line[line.length() - 1] == '\r') {
            line.erase(line.length() - 1);
        }
        if (!line.empty()) {
            dict.push_back(line);
        }
    }
    file.close();

    LOGD("Diccionario cargado: %zu caracteres", dict.size());
    return dict;
}

// Función auxiliar: Convertir RGBA a RGB normalizado
void rgbaToRgbNormalized(const uint8_t *rgba_data, int width, int height,
                          std::vector<float> &rgb_data) {
    rgb_data.resize(3 * width * height);

    // Mean y std para normalización
    float mean[3] = {0.485f, 0.456f, 0.406f};
    float std[3] = {0.229f, 0.224f, 0.225f};

    int hw = height * width;

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            int idx = h * width + w;
            int rgba_idx = idx * 4;

            float r = rgba_data[rgba_idx] / 255.0f;
            float g = rgba_data[rgba_idx + 1] / 255.0f;
            float b = rgba_data[rgba_idx + 2] / 255.0f;

            rgb_data[idx] = (r - mean[0]) / std[0];
            rgb_data[hw + idx] = (g - mean[1]) / std[1];
            rgb_data[2 * hw + idx] = (b - mean[2]) / std[2];
        }
    }
}

// Función auxiliar: Redimensionar imagen manteniendo aspect ratio
void resizeKeepAspectRatio(const std::vector<float> &src, int src_w, int src_h,
                            std::vector<float> &dst, int &dst_w, int &dst_h,
                            int max_side_len = 960) {
    // Calcular nuevo tamaño manteniendo aspect ratio
    float ratio = 1.0f;
    if (std::max(src_w, src_h) > max_side_len) {
        if (src_h > src_w) {
            ratio = (float)max_side_len / src_h;
        } else {
            ratio = (float)max_side_len / src_w;
        }
    }

    dst_w = (int)(src_w * ratio);
    dst_h = (int)(src_h * ratio);

    // Asegurar que sea múltiplo de 32 (requerido por el modelo)
    dst_w = (dst_w / 32) * 32;
    dst_h = (dst_h / 32) * 32;

    dst.resize(3 * dst_w * dst_h);

    float scale_x = (float)src_w / dst_w;
    float scale_y = (float)src_h / dst_h;

    int src_hw = src_h * src_w;
    int dst_hw = dst_h * dst_w;

    for (int c = 0; c < 3; c++) {
        for (int h = 0; h < dst_h; h++) {
            for (int w = 0; w < dst_w; w++) {
                int src_x = (int)(w * scale_x);
                int src_y = (int)(h * scale_y);

                src_x = std::min(src_x, src_w - 1);
                src_y = std::min(src_y, src_h - 1);

                int src_idx = c * src_hw + src_y * src_w + src_x;
                int dst_idx = c * dst_hw + h * dst_w + w;

                dst[dst_idx] = src[src_idx];
            }
        }
    }

    LOGD("Imagen redimensionada: %dx%d → %dx%d (ratio: %.2f)", src_w, src_h, dst_w, dst_h, ratio);
}

#include <queue>


// Función de detección de cajas de texto (mejorada con componentes conectados)
std::vector<TextBox> detectTextBoxes(const std::vector<float> &image_data, int width, int height) {
    std::vector<TextBox> boxes;

    if (!g_context.det_predictor) {
        LOGE("Predictor de detección no inicializado");
        return boxes;
    }

    try {
        // Preparar input tensor
        auto input_tensor = g_context.det_predictor->GetInput(0);
        input_tensor->Resize({1, 3, height, width});
        auto *input_data = input_tensor->mutable_data<float>();
        memcpy(input_data, image_data.data(), image_data.size() * sizeof(float));

        // Ejecutar predicción
        g_context.det_predictor->Run();

        // Obtener salida (mapa de probabilidad)
        auto output_tensor = g_context.det_predictor->GetOutput(0);
        auto output_shape = output_tensor->shape();
        auto *output_data = output_tensor->data<float>();

        int out_h = output_shape[2];
        int out_w = output_shape[3];

        LOGD("Detección ejecutada. Salida: %dx%d", out_w, out_h);

        // Post-procesamiento mejorado: componentes conectados
        float threshold = 0.3f; // Reducir umbral para capturar más detalles
        int min_area = 50;     // Área mínima para ser considerada una caja de texto

        // Binarizar el mapa de probabilidad
        std::vector<uint8_t> binary_map(out_h * out_w);
        for (int i = 0; i < out_h * out_w; i++) {
            binary_map[i] = output_data[i] > threshold ? 1 : 0;
        }

        std::vector<bool> visited(out_h * out_w, false);

        for (int y = 0; y < out_h; ++y) {
            for (int x = 0; x < out_w; ++x) {
                int index = y * out_w + x;
                if (binary_map[index] == 1 && !visited[index]) {
                    std::vector<std::pair<int, int>> component_points;
                    std::queue<std::pair<int, int>> q;
                    
                    q.push({x, y});
                    visited[index] = true;
                    
                    int min_x = x, max_x = x, min_y = y, max_y = y;
                    float total_score = 0;

                    while (!q.empty()) {
                        std::pair<int, int> current = q.front();
                        q.pop();
                        
                        component_points.push_back(current);
                        min_x = std::min(min_x, current.first);
                        max_x = std::max(max_x, current.first);
                        min_y = std::min(min_y, current.second);
                        max_y = std::max(max_y, current.second);
                        total_score += output_data[current.second * out_w + current.first];

                        // Explorar vecinos (4 direcciones)
                        int dx[] = {0, 0, 1, -1};
                        int dy[] = {1, -1, 0, 0};

                        for (int i = 0; i < 4; ++i) {
                            int nx = current.first + dx[i];
                            int ny = current.second + dy[i];
                            int n_index = ny * out_w + nx;

                            if (nx >= 0 && nx < out_w && ny >= 0 && ny < out_h &&
                                binary_map[n_index] == 1 && !visited[n_index]) {
                                visited[n_index] = true;
                                q.push({nx, ny});
                            }
                        }
                    }

                    if (component_points.size() > min_area) {
                        TextBox box;
                        box.score = total_score / component_points.size();

                        float scale_x = (float)width / out_w;
                        float scale_y = (float)height / out_h;

                        box.points = {
                            {(int)(min_x * scale_x), (int)(min_y * scale_y)},
                            {(int)(max_x * scale_x), (int)(min_y * scale_y)},
                            {(int)(max_x * scale_x), (int)(max_y * scale_y)},
                            {(int)(min_x * scale_x), (int)(max_y * scale_y)}
                        };
                        boxes.push_back(box);
                    }
                }
            }
        }
        
        LOGD("Cajas detectadas (post-procesado): %zu", boxes.size());

    } catch (const std::exception &e) {
        LOGE("Error en detección: %s", e.what());
    }

    return boxes;
}

// Función auxiliar: Recortar región de imagen
void cropImage(const std::vector<float> &src, int src_w, int src_h,
               const TextBox &box, std::vector<float> &dst, int &dst_w, int &dst_h) {
    // Calcular bounding box
    int min_x = src_w, max_x = 0, min_y = src_h, max_y = 0;
    for (const auto &point : box.points) {
        min_x = std::min(min_x, point.first);
        max_x = std::max(max_x, point.first);
        min_y = std::min(min_y, point.second);
        max_y = std::max(max_y, point.second);
    }

    // Asegurar límites
    min_x = std::max(0, min_x);
    min_y = std::max(0, min_y);
    max_x = std::min(src_w - 1, max_x);
    max_y = std::min(src_h - 1, max_y);

    dst_w = max_x - min_x;
    dst_h = max_y - min_y;

    if (dst_w <= 0 || dst_h <= 0) {
        dst_w = dst_h = 0;
        return;
    }

    dst.resize(3 * dst_w * dst_h);

    int src_hw = src_h * src_w;
    int dst_hw = dst_h * dst_w;

    for (int c = 0; c < 3; c++) {
        for (int y = 0; y < dst_h; y++) {
            for (int x = 0; x < dst_w; x++) {
                int src_idx = c * src_hw + (min_y + y) * src_w + (min_x + x);
                int dst_idx = c * dst_hw + y * dst_w + x;
                dst[dst_idx] = src[src_idx];
            }
        }
    }
}

// Función auxiliar: Redimensionar para reconocimiento a tamaño FIJO 320x32
void resizeForRecognition(const std::vector<float> &src, int src_w, int src_h,
                          std::vector<float> &dst, int &dst_w, int &dst_h) {
    const int rec_height = 32;   // Altura fija
    const int rec_width = 320;   // Ancho fijo (PP-OCRv3 usa 320px)

    // Validar dimensiones de entrada
    if (src_w <= 0 || src_h <= 0) {
        LOGE("Dimensiones inválidas para reconocimiento: %dx%d", src_w, src_h);
        dst_w = 0;
        dst_h = 0;
        return;
    }

    // Siempre redimensionar a exactamente 120x32 (estirando si es necesario)
    dst_w = rec_width;
    dst_h = rec_height;

    LOGD("Redimensionando para reconocimiento: %dx%d → %dx%d",
         src_w, src_h, dst_w, dst_h);

    dst.resize(3 * dst_w * dst_h);

    // Escala para redimensionar (puede distorsionar aspect ratio)
    float scale_x = (float)src_w / dst_w;
    float scale_y = (float)src_h / dst_h;

    int src_hw = src_h * src_w;
    int dst_hw = dst_h * dst_w;

    // Redimensionar TODA la imagen a 120x32
    for (int c = 0; c < 3; c++) {
        for (int h = 0; h < dst_h; h++) {
            for (int w = 0; w < dst_w; w++) {
                int src_x = (int)(w * scale_x);
                int src_y = (int)(h * scale_y);

                src_x = std::min(src_x, src_w - 1);
                src_y = std::min(src_y, src_h - 1);

                int src_idx = c * src_hw + src_y * src_w + src_x;
                int dst_idx = c * dst_hw + h * dst_w + w;

                dst[dst_idx] = src[src_idx];
            }
        }
    }
}

// Función de reconocimiento de texto
std::string recognizeText(const std::vector<float> &image_data, int width, int height) {
    if (!g_context.rec_predictor || g_context.char_dict.empty()) {
        LOGE("Predictor o diccionario no inicializado");
        return "";
    }

    try {
        // Preparar input tensor
        auto input_tensor = g_context.rec_predictor->GetInput(0);
        input_tensor->Resize({1, 3, height, width});
        auto *input_data = input_tensor->mutable_data<float>();
        memcpy(input_data, image_data.data(), image_data.size() * sizeof(float));

        // Ejecutar predicción
        g_context.rec_predictor->Run();

        // Obtener salida
        auto output_tensor = g_context.rec_predictor->GetOutput(0);
        auto output_shape = output_tensor->shape();
        auto *output_data = output_tensor->data<float>();

        // Decodificar resultado (CTC decoding)
        int seq_len = output_shape[1];
        int char_num = output_shape[2];

        std::string text;
        int last_idx = -1;

        for (int t = 0; t < seq_len; t++) {
            int max_idx = 0;
            float max_val = output_data[t * char_num];

            for (int c = 1; c < char_num; c++) {
                if (output_data[t * char_num + c] > max_val) {
                    max_val = output_data[t * char_num + c];
                    max_idx = c;
                }
            }

            // CTC: ignorar blank (0) y repeticiones
            if (max_idx > 0 && max_idx != last_idx) {
                if (max_idx - 1 < (int)g_context.char_dict.size()) {
                    text += g_context.char_dict[max_idx - 1];
                }
            }
            last_idx = max_idx;
        }

        return text;

    } catch (const std::exception &e) {
        LOGE("Error en reconocimiento: %s", e.what());
        return "";
    }
}

// JNI: Inicializar PaddleOCR
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_soflex_lectorpatente_PaddleOCRPredictor_nativeInit(
        JNIEnv *env,
        jobject thiz,
        jstring det_model_path,
        jstring rec_model_path,
        jstring cls_model_path,
        jstring dict_path,
        jint num_threads) {

    LOGD("Inicializando PaddleOCR...");

    try {
        const char *det_path_cstr = env->GetStringUTFChars(det_model_path, nullptr);
        const char *rec_path_cstr = env->GetStringUTFChars(rec_model_path, nullptr);
        const char *cls_path_cstr = env->GetStringUTFChars(cls_model_path, nullptr);
        const char *dict_path_cstr = env->GetStringUTFChars(dict_path, nullptr);

        std::string det_path(det_path_cstr);
        std::string rec_path(rec_path_cstr);
        std::string cls_path(cls_path_cstr);
        std::string dict_path_str(dict_path_cstr);

        env->ReleaseStringUTFChars(det_model_path, det_path_cstr);
        env->ReleaseStringUTFChars(rec_model_path, rec_path_cstr);
        env->ReleaseStringUTFChars(cls_model_path, cls_path_cstr);
        env->ReleaseStringUTFChars(dict_path, dict_path_cstr);

        LOGD("Cargando modelos...");

        MobileConfig det_config;
        det_config.set_model_from_file(det_path);
        det_config.set_threads(num_threads);
        det_config.set_power_mode(PowerMode::LITE_POWER_HIGH);

        MobileConfig rec_config;
        rec_config.set_model_from_file(rec_path);
        rec_config.set_threads(num_threads);
        rec_config.set_power_mode(PowerMode::LITE_POWER_HIGH);

        MobileConfig cls_config;
        cls_config.set_model_from_file(cls_path);
        cls_config.set_threads(num_threads);
        cls_config.set_power_mode(PowerMode::LITE_POWER_HIGH);

        LOGD("Creando predictores...");
        g_context.det_predictor = CreatePaddlePredictor<MobileConfig>(det_config);
        g_context.rec_predictor = CreatePaddlePredictor<MobileConfig>(rec_config);
        g_context.cls_predictor = CreatePaddlePredictor<MobileConfig>(cls_config);

        LOGD("Cargando diccionario...");
        g_context.char_dict = loadCharDict(dict_path_str);

        if (g_context.char_dict.empty()) {
            LOGE("Diccionario vacío");
            return JNI_FALSE;
        }

        g_context.initialized = true;
        LOGD("✅ PaddleOCR inicializado exitosamente");
        LOGD("   Hilos: %d", num_threads);
        LOGD("   Caracteres en diccionario: %zu", g_context.char_dict.size());

        return JNI_TRUE;

    } catch (const std::exception &e) {
        LOGE("❌ Error inicializando PaddleOCR: %s", e.what());
        return JNI_FALSE;
    }
}

// JNI: Procesar imagen con PaddleOCR completo
extern "C"
JNIEXPORT jstring JNICALL
Java_com_soflex_lectorpatente_PaddleOCRPredictor_nativeRunOCR(
        JNIEnv *env,
        jobject thiz,
        jobject bitmap) {

    if (!g_context.initialized) {
        LOGE("PaddleOCR no inicializado");
        return env->NewStringUTF("");
    }

    try {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
            LOGE("No se pudo obtener info del Bitmap");
            return env->NewStringUTF("");
        }

        void *pixels;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
            LOGE("No se pudo bloquear pixels del Bitmap");
            return env->NewStringUTF("");
        }

        int width = info.width;
        int height = info.height;

        LOGD("═══════════════════════════════════");
        LOGD("Procesando imagen: %dx%d", width, height);

        // Convertir RGBA a RGB normalizado
        std::vector<float> rgb_data;
        rgbaToRgbNormalized((uint8_t *)pixels, width, height, rgb_data);

        AndroidBitmap_unlockPixels(env, bitmap);

        // 1. Redimensionar para detección
        std::vector<float> resized_for_det;
        int det_w, det_h;
        resizeKeepAspectRatio(rgb_data, width, height, resized_for_det, det_w, det_h);

        // 2. Detectar cajas de texto
        std::vector<TextBox> text_boxes = detectTextBoxes(resized_for_det, det_w, det_h);

        if (text_boxes.empty()) {
            LOGD("No se detectaron cajas de texto");
            return env->NewStringUTF("");
        }

        LOGD("Procesando %zu regiones detectadas", text_boxes.size());

        // 3. Reconocer texto en cada caja
        std::string full_text;
        for (size_t i = 0; i < std::min(text_boxes.size(), (size_t)10); i++) {
            // Recortar región
            std::vector<float> cropped;
            int crop_w, crop_h;
            cropImage(resized_for_det, det_w, det_h, text_boxes[i], cropped, crop_w, crop_h);

            // Validar tamaño mínimo (filtrar regiones muy pequeñas o inválidas)
            // También filtrar regiones con aspect ratio extremo (muy estrechas o muy altas)
            if (crop_w < 10 || crop_h < 10) {
                LOGD("Región %zu muy pequeña: %dx%d. Saltando.", i, crop_w, crop_h);
                continue;
            }

            // Filtrar aspect ratios extremos (evitar regiones que no sean texto)
            float aspect_ratio = (float)crop_w / crop_h;
            if (aspect_ratio < 0.2f || aspect_ratio > 20.0f) {
                LOGD("Región %zu con aspect ratio extremo: %.2f (%dx%d). Saltando.",
                     i, aspect_ratio, crop_w, crop_h);
                continue;
            }

            // Redimensionar para reconocimiento
            std::vector<float> resized_for_rec;
            int rec_w, rec_h;
            resizeForRecognition(cropped, crop_w, crop_h, resized_for_rec, rec_w, rec_h);

            // Validar dimensiones antes de reconocimiento (DEBE ser exactamente 320x32)
            if (rec_w != 320 || rec_h != 32) {
                LOGE("Dimensiones inválidas después de resize: %dx%d (esperado: 320x32). Saltando región %zu",
                     rec_w, rec_h, i);
                continue;
            }

            LOGD("Región %zu: crop=%dx%d, rec=%dx%d", i, crop_w, crop_h, rec_w, rec_h);

            // Reconocer texto
            std::string text = recognizeText(resized_for_rec, rec_w, rec_h);

            if (!text.empty()) {
                if (!full_text.empty()) {
                    full_text += " ";
                }
                full_text += text;
                LOGD("Región %zu: '%s' (score: %.2f)", i, text.c_str(), text_boxes[i].score);
            }
        }

        LOGD("Texto completo: %s", full_text.c_str());
        LOGD("═══════════════════════════════════");

        return env->NewStringUTF(full_text.c_str());

    } catch (const std::exception &e) {
        LOGE("Error procesando imagen: %s", e.what());
        return env->NewStringUTF("");
    }
}

// JNI: Liberar recursos
extern "C"
JNIEXPORT void JNICALL
Java_com_soflex_lectorpatente_PaddleOCRPredictor_nativeRelease(
        JNIEnv *env,
        jobject thiz) {

    LOGD("Liberando recursos de PaddleOCR...");

    g_context.det_predictor.reset();
    g_context.rec_predictor.reset();
    g_context.cls_predictor.reset();
    g_context.char_dict.clear();
    g_context.initialized = false;

    LOGD("Recursos liberados");
}