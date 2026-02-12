cmake_minimum_required(VERSION 3.18.1)

project("paddleocr_jni")

# Configuración de C++
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14 -frtti -fexceptions -O3")

# Flags del linker para compatibilidad con Paddle-Lite (quitar --fatal-warnings)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--allow-multiple-definition -Wl,--no-fatal-warnings")

# Ruta a las librerías de Paddle-Lite
set(PADDLE_LITE_DIR ${CMAKE_SOURCE_DIR}/paddlelite_libs/inference_lite_lib.android.armv8.gcc.c++_shared.with_extra.with_cv)

# Incluir directorios de headers
include_directories(
    ${PADDLE_LITE_DIR}/cxx/include
)

# Agregar librería compartida para Paddle-Lite
add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

# Crear nuestra librería JNI
add_library(
    paddleocr_jni
    SHARED
    paddle_ocr_jni.cpp
)

# Buscar librería de log de Android
find_library(log-lib log)

# Buscar librería jnigraphics para manejo de Bitmaps
find_library(jnigraphics-lib jnigraphics)

# Linkear librerías
target_link_libraries(
    paddleocr_jni
    paddle_light_api_shared
    ${log-lib}
    ${jnigraphics-lib}
    z
)