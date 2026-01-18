# XmovLiteAvatar Android Demo - 项目总结

## 项目概述
基于 Xmov 数字人 SDK 的 Android 演示应用，集成了 TRPG（桌面角色扮演游戏）功能、AI 剧本生成、动作识别等特性。

## 🚨 当前状态：编译失败
**主要问题**: Kotlin 版本兼容性 - SDK AAR 使用 Kotlin 2.2.0，项目使用 Kotlin 2.0.21

## 核心功能模块

### 1. 数字人 SDK 集成 ❌
- **文件**: `MainActivity.kt`, `IXmovAvatar` 接口
- **功能**: 数字人渲染、语音合成、表情控制
- **状态**: 无法编译 - Kotlin 版本不兼容

### 2. TRPG 游戏系统 ⚠️
- **文件**: `app/src/main/java/com/xmov/metahuman/app/trpg/`
- **功能**: 
  - 剧本解析 (`ScriptParser.kt`)
  - AI 剧本生成 (`AIScriptGenerator.kt`) 
  - 游戏引擎 (`TRPGGameEngine.kt`)
  - 故事树结构 (`StoryTree.kt`)
- **支持游戏类型**: 剧本杀、跑团、海龟汤
- **状态**: 部分可用 - 需修复导入问题

### 3. LLM 集成 ✅
- **文件**: `app/src/main/java/com/xmov/metahuman/app/llm/`
- **功能**: APIMart 客户端、文本处理工具
- **状态**: 基本可用

### 4. 动作识别系统 ❌
- **文件**: `app/src/main/java/com/xmov/metahuman/app/pose/`
- **功能**: MediaPipe 姿态检测、动作分类
- **状态**: 缺少依赖导入

### 5. 情感检测 ❌
- **文件**: `app/src/main/java/com/xmov/metahuman/app/emotion/`
- **功能**: 陀螺仪监控、情感分析
- **状态**: 缺少协程支持

### 6. 数据库系统 ❌
- **文件**: `app/src/main/java/com/xmov/metahuman/app/gameplay/`
- **功能**: 角色系统、成就系统、社交功能
- **状态**: Room 依赖已禁用

### 7. 图像生成 ⚠️
- **文件**: `app/src/main/java/com/xmov/metahuman/app/imagegen/`
- **功能**: AI 图像生成客户端
- **状态**: 需修复 JSON 导入

## API 接口说明

### TRPG 核心 API
```kotlin
// 剧本生成
AIScriptGenerator.generateScript(theme: String, gameType: GameType, sceneCount: Int): Result<StoryTree>

// 游戏引擎
TRPGGameEngine.startGame(storyTree: StoryTree)
TRPGGameEngine.processPlayerAction(action: PlayerAction): GameState

// 剧本解析
ScriptParser.parseFile(file: File): StoryTree
ScriptParser.parseJsonContent(json: String, gameType: GameType): StoryTree
```

### LLM 集成 API
```kotlin
// APIMart 客户端
ApimartClient.chatCompletions(apiKey: String, baseUrl: String, model: String, messages: JSONArray): Result<String>

// 文本处理
LlmTextUtils.extractJsonObject(text: String): String?
LlmTextUtils.cleanText(text: String): String
```

### 数字人控制 API（当前不可用）
```kotlin
// 数字人控制
IXmovAvatar.startRender()
IXmovAvatar.speak(text: String)
IXmovAvatar.setEmotion(emotion: String)
```

## 配置文件

### 应用设置
- **文件**: `app/src/main/java/com/xmov/metahuman/app/AppSettings.kt`
- **功能**: LLM API 配置、应用偏好设置

### 示例数据
- **位置**: `app/src/main/assets/`
- **文件**: 
  - `demo_configs.json` - 演示配置
  - `sample_*.json` - 各类游戏示例剧本
  - `pose_landmarker_lite.task` - MediaPipe 模型

## 构建配置

### 依赖管理
- **Kotlin**: 2.0.21 (需升级到 2.2.0)
- **Android Gradle Plugin**: 8.12.1
- **编译 SDK**: 36
- **最小 SDK**: 24

### 关键依赖
```kotlin
// 核心
implementation("androidx.core:core-ktx:1.17.0")
implementation("androidx.appcompat:appcompat:1.7.1")

// 协程
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// 网络
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.google.code.gson:gson:2.13.1")

// MediaPipe (需修复)
implementation("com.google.mediapipe:tasks-vision:0.10.14")

// 相机
implementation("androidx.camera:camera-core:1.3.4")
```

## 修复建议

### 立即修复（让项目编译通过）
1. 升级 Kotlin 到 2.2.0
2. 添加缺失的导入语句
3. 修复类型不匹配问题

### 长期优化
1. 获取兼容版本的 SDK AAR
2. 重新启用 Room 数据库
3. 完善错误处理和日志记录

## 文档文件
- `BUILD_FIX_GUIDE.md` - 详细的构建修复指南
- `COMPLETE_PROJECT_DOCUMENTATION.md` - 完整项目文档
- `FEATURES_README.md` - 功能特性说明

---
**项目规模**: ~50 个 Kotlin 文件，~8000 行代码
**主要挑战**: Kotlin 版本兼容性
**预计修复时间**: 2-4 小时（临时修复）/ 1-2 天（完整修复）