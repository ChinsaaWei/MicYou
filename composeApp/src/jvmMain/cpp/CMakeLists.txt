cmake_minimum_required(VERSION 3.18)
project(audio_processor)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

set(ONNXRUNTIME_BASE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ONNX_Runtime_1.14.1_Bin_Include_All_Platforms)

if(WIN32)
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(ONNXRUNTIME_PLATFORM_DIR onnxruntime-win-x64-1.14.1)
    else()
        set(ONNXRUNTIME_PLATFORM_DIR onnxruntime-win-x86-1.14.1)
    endif()
elseif(APPLE)
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64")
        set(ONNXRUNTIME_PLATFORM_DIR onnxruntime-osx-arm64-1.14.1)
    else()
        set(ONNXRUNTIME_PLATFORM_DIR onnxruntime-osx-x64-1.14.1)
    endif()
elseif(LINUX)
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
        set(ONNXRUNTIME_PLATFORM_DIR onnxruntime-linux-aarch64-1.14.1)
    else()
        set(ONNXRUNTIME_PLATFORM_DIR onnxruntime-linux-x64-1.14.1)
    endif()
endif()

set(ONNXRUNTIME_DIR ${ONNXRUNTIME_BASE_DIR}/${ONNXRUNTIME_PLATFORM_DIR})
set(ONNXRUNTIME_INCLUDE_DIR ${ONNXRUNTIME_DIR}/include)
set(ONNXRUNTIME_LIB_DIR ${ONNXRUNTIME_DIR}/lib)

if(WIN32)
    if(NOT EXISTS ${ONNXRUNTIME_LIB_DIR}/onnxruntime.lib)
        message(FATAL_ERROR "ONNX Runtime 预编译库未找到！\n"
                            "缺失文件: ${ONNXRUNTIME_LIB_DIR}/onnxruntime.lib")
    endif()
elseif(APPLE)
    if(NOT EXISTS ${ONNXRUNTIME_LIB_DIR}/libonnxruntime.dylib)
        message(FATAL_ERROR "ONNX Runtime 预编译库未找到！\n"
                            "缺失文件: ${ONNXRUNTIME_LIB_DIR}/libonnxruntime.dylib")
    endif()
else()
    if(NOT EXISTS ${ONNXRUNTIME_LIB_DIR}/libonnxruntime.so)
        message(FATAL_ERROR "ONNX Runtime 预编译库未找到！\n"
                            "缺失文件: ${ONNXRUNTIME_LIB_DIR}/libonnxruntime.so")
    endif()
endif()

include_directories(
    ${JNI_INCLUDE_DIRS}
    ${ONNXRUNTIME_INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/pffft
)

link_directories(${ONNXRUNTIME_LIB_DIR})

set(PFFFT_SOURCES
    pffft/pffft.c
    pffft/pffft_common.c
)

add_library(audio_processor SHARED
    audio_processor.cpp
    ${PFFFT_SOURCES}
)

if(WIN32)
    target_link_libraries(audio_processor
        ${JNI_LIBRARIES}
        ${ONNXRUNTIME_LIB_DIR}/onnxruntime.lib
    )
else()
    target_link_libraries(audio_processor
        ${JNI_LIBRARIES}
        onnxruntime
    )
endif()

if(WIN32)
    target_compile_definitions(audio_processor PRIVATE _WINDOWS)
    set_target_properties(audio_processor PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    )
endif()

install(TARGETS audio_processor
    RUNTIME DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/../resources
)

if(WIN32)
    add_custom_command(TARGET audio_processor POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            ${ONNXRUNTIME_LIB_DIR}/onnxruntime.dll
            ${CMAKE_CURRENT_SOURCE_DIR}/../resources
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            ${ONNXRUNTIME_LIB_DIR}/onnxruntime_providers_shared.dll
            ${CMAKE_CURRENT_SOURCE_DIR}/../resources
        COMMENT "复制 ONNX Runtime DLL 到资源目录"
    )
endif()

message(STATUS "==========================================")
message(STATUS "audio_processor 配置完成")
message(STATUS "平台: ${ONNXRUNTIME_PLATFORM_DIR}")
message(STATUS "ONNX Runtime 路径: ${ONNXRUNTIME_DIR}")
message(STATUS "输出目录: ${CMAKE_CURRENT_SOURCE_DIR}/../resources")
message(STATUS "==========================================")
