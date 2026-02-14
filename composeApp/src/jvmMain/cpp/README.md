# Audio Processor Native Library 编译指南

本文档说明如何编译 `audio_processor` 原生库，该库用于 Ulunas AI 降噪功能。

## 依赖说明

### ONNX Runtime

项目使用 **ONNX Runtime 1.14.1** 官方预编译版本，无需自行编译 ONNX Runtime。

预编译库位于：
```
composeApp/src/jvmMain/cpp/ONNX_Runtime_1.14.1_Bin_Include_All_Platforms/
├── onnxruntime-win-x64-1.14.1/          # Windows x64
│   ├── include/                          # 头文件
│   └── lib/
│       ├── onnxruntime.dll               # 动态库
│       ├── onnxruntime.lib               # 导入库
│       ├── onnxruntime_providers_shared.dll
│       └── onnxruntime_providers_shared.lib
├── onnxruntime-win-arm64-1.14.1/         # Windows ARM64
├── onnxruntime-linux-x64-1.14.1/         # Linux x64
│   └── lib/
│       └── libonnxruntime.so
├── onnxruntime-linux-aarch64-1.14.1/     # Linux ARM64
└── onnxruntime-osx-arm64-1.14.1/         # macOS ARM64
    └── lib/
        └── libonnxruntime.dylib
```

> **来源**: [ONNX Runtime Releases](https://github.com/microsoft/onnxruntime/releases/tag/v1.14.1)

### pffft

pffft (Pretty Fast FFT) 源码已包含在项目中：
```
composeApp/src/jvmMain/cpp/pffft/
├── pffft.c
├── pffft.h
├── pffft_common.c
└── simd/                    # SIMD 优化代码
```

## 编译步骤

### Windows (x64)

**前置条件:**
- CMake 3.18+
- Visual Studio 2019+ 或 Build Tools for Visual Studio
- JDK 17 (设置 `JAVA_HOME` 环境变量)

**编译命令:**
```powershell
cd composeApp/src/jvmMain/cpp
mkdir build
cd build
cmake -G "Visual Studio 17 2022" -A x64 ..
cmake --build . --config Release
```

**输出文件:**
- `audio_processor.dll` → 复制到 `composeApp/src/jvmMain/resources/`
- `onnxruntime.dll` → 复制到 `composeApp/src/jvmMain/resources/`
- `onnxruntime_providers_shared.dll` → 复制到 `composeApp/src/jvmMain/resources/`

### Linux (x64)

**前置条件:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install cmake g++ openjdk-17-jdk

# Fedora
sudo dnf install cmake gcc-c++ java-17-openjdk-devel
```

**编译命令:**
```bash
cd composeApp/src/jvmMain/cpp
mkdir build && cd build
cmake ..
make -j$(nproc)
```

**输出文件:**
- `libaudio_processor.so` → 复制到 `composeApp/src/jvmMain/resources/`
- `libonnxruntime.so` → 复制到 `composeApp/src/jvmMain/resources/`

### Linux (ARM64/aarch64)

**前置条件:**
```bash
# Ubuntu/Debian (ARM64 设备上)
sudo apt update
sudo apt install cmake g++ openjdk-17-jdk
```

**编译命令:**
```bash
cd composeApp/src/jvmMain/cpp
mkdir build && cd build
cmake ..
make -j$(nproc)
```

### macOS (ARM64/Apple Silicon)

**前置条件:**
```bash
# 安装 Xcode Command Line Tools
xcode-select --install

# 安装 Homebrew (如果没有)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装 CMake
brew install cmake

# 安装 JDK 17
brew install openjdk@17
```

**编译命令:**
```bash
cd composeApp/src/jvmMain/cpp
mkdir build && cd build
cmake -DCMAKE_OSX_ARCHITECTURES=arm64 ..
make -j$(sysctl -n hw.ncpu)
```

**输出文件:**
- `libaudio_processor.dylib` → 复制到 `composeApp/src/jvmMain/resources/`
- `libonnxruntime.dylib` → 复制到 `composeApp/src/jvmMain/resources/`

### macOS (x64/Intel)

**编译命令:**
```bash
cd composeApp/src/jvmMain/cpp
mkdir build && cd build
cmake -DCMAKE_OSX_ARCHITECTURES=x86_64 ..
make -j$(sysctl -n hw.ncpu)
```

## 文件清单

编译完成后，确保以下文件位于 `composeApp/src/jvmMain/resources/` 目录：

| 平台 | 必需文件 |
|------|----------|
| Windows | `audio_processor.dll`, `onnxruntime.dll`, `onnxruntime_providers_shared.dll` |
| Linux | `libaudio_processor.so`, `libonnxruntime.so` |
| macOS | `libaudio_processor.dylib`, `libonnxruntime.dylib` |


## 相关链接

- [ONNX Runtime GitHub](https://github.com/microsoft/onnxruntime)
- [ONNX Runtime Documentation](https://onnxruntime.ai/docs/)
- [pffft GitHub](https://github.com/marton78/pffft)
