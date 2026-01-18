# MediaPipe Pose 模型设置

## 下载模型文件

MediaPipe Pose 模型需要手动下载并放置到项目中：

1. **下载模型文件**

   访问 MediaPipe GitHub Releases:
   https://github.com/google/mediapipe/releases

   下载 `pose_landmarker_lite.task` 文件 (约 10MB)

2. **放置模型文件**

   将下载的文件放置到项目的 `src/main/assets/` 目录下：

   ```
   app/src/main/assets/pose_landmarker_lite.task
   ```

3. **目录结构**

   最终的 assets 目录结构应如下：

   ```
   app/src/main/assets/
   ├── pose_landmarker_lite.task          ← MediaPipe Pose 模型
   ├── demo_configs.json                  ← 数字人配置
   ├── demo_speak_value.txt               ← 数字人测试文本
   └── MockAudioInputsData.json           ← 数字人音频输入示例
   ```

## 模型选择

MediaPipe 提供三种 Pose 模型：

| 模型 | 大小 | 精度 | 速度 | 推荐场景 |
|------|------|------|------|----------|
| `pose_landmarker_lite.task` | ~10MB | 中等 | 快 | 实时检测，推荐 |
| `pose_landmarker_full.task` | ~50MB | 高 | 中等 | 高精度场景 |
| `pose_landmarker_heavy.task` | ~90MB | 极高 | 慢 | 离线分析 |

对于 TRPG 游戏，推荐使用 **lite** 版本以获得实时性能。

## 代码配置

在 `PoseDetector.kt` 中修改模型路径：

```kotlin
val baseOptions = BaseOptions.builder()
    .setModelAssetPath("pose_landmarker_lite.task")  // 或其他模型
    .build()
```

## 权限要求

确保在 `AndroidManifest.xml` 中添加相机权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

## 运行时权限请求

在需要使用相机时请求权限：

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        CAMERA_PERMISSION_REQUEST_CODE
    )
}
```

## 故障排除

### 模型文件找不到

错误: `Failed to initialize pose detector: Asset not found`

**解决**: 确保模型文件正确放置在 `app/src/main/assets/` 目录下

### 相机权限被拒绝

错误: `Permission Denial: starting Intent requires android.permission.CAMERA`

**解决**: 在设置中授予相机权限，或在代码中动态请求权限

### 检测精度不够

**解决**: 尝试使用 `full` 或 `heavy` 版本的模型

## 性能优化

1. **降低检测频率**
   ```kotlin
   ImageAnalysis.Builder()
       .setTargetResolution(android.util.Size(640, 480))  // 降低分辨率
       .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
   ```

2. **使用 lite 模型**
   - `pose_landmarker_lite.task` 比 full 版本快 5-10 倍

3. **后台暂停检测**
   ```kotlin
   override fun onPause() {
       super.onPause()
       poseCameraManager.stopCamera()
   }
   ```

## 参考链接

- MediaPipe 官方文档: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
- MediaPipe GitHub: https://github.com/google/mediapipe
- MediaPipe Releases: https://github.com/google/mediapipe/releases
