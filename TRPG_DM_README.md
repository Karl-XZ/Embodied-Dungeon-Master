# TRPG DM数字主持系统

## 项目概述

基于XmovLiteAvatar Android数字人SDK改造的TRPG（桌上角色扮演游戏）DM（Dungeon Master）数字主持系统。

### 核心功能

1. **三种游戏模式**
   - 📜 **剧本杀**：悬疑推理，搜集线索，找出真相
   - 🎲 **跑团**：奇幻冒险，骰点判定，团队协作
   - 🍲 **海龟汤**：提问推理，逐步揭开谜团

2. **多人房间系统**
   - 房主创建房间，设置人数限制和密码
   - 成员加入房间，实时同步游戏状态
   - 支持房主开始/结束游戏

3. **智能剧情树引擎**
   - 节点=场景，边=转场条件
   - 支持关键转折点、目标、线索池
   - Fail-Safe纠偏机制，保证剧情不跑偏

4. **数字人DM集成**
   - 数字人朗读场景描述和剧情文本
   - 实时语音交互
   - 情感和动作反馈

5. **规则裁决系统**
   - Rules Arbiter：骰点判定（D20/D100/D6等）
   - Clue Master：线索分发（公开/私密）
   - Scene Director：剧情推进控制

6. **游戏复盘**
   - 剧情路径记录
   - 关键决策统计
   - 线索发放追踪
   - 骰点数据统计

## 新增高级功能

> 详细说明请查看 [FEATURES_README.md](FEATURES_README.md)

### 🖼️ 自动图片生成
- 使用 APIMart 生图 API (Stable Diffusion XL / DALL-E 3)
- 自动生成线索卡图片、场景 CG
- 支持批量生成，图片缓存管理

### 🏃 玩家动作识别与惩罚系统
- MediaPipe Pose 端侧姿态检测
- 支持动作：下蹲、跳跃、闪避、举手、蹲伏等
- 陀螺仪检测：点头、摇头、举起设备
- 惩罚系统：骰点失败需执行动作惩罚

### 😊 玩家情绪状态识别
- 通义千问 VL (Qwen-VL) API 拍照识别
- 识别情绪、注意力、精力、疲劳、压力、投入度
- 情绪状态作为 LLM 输入上下文

### 🌐 网络多人同步
- Socket.IO 实时同步
- 房间状态、聊天、骰点实时同步
- 玩家在线状态管理

### 🤖 Agent 服务端
- 剧本智能解析（自然语言 → 结构化剧情树）
- 剧情树自动生成
- Fail-Safe 策略自动生成

### 🎭 角色系统
- 角色创建与自定义
- 属性系统 (6维属性)
- 技能系统与升级
- 物品/背包系统
- 经验值与等级

### 🏅 成就系统
- 多类别成就 (剧情/战斗/探索/社交/收集/挑战)
- 成就进度追踪
- 成就解锁奖励

### 👥 社交功能
- 好友系统 (添加/删除/屏蔽)
- 房间收藏
- 游戏记录
- 复盘分享

## 技术架构

### 核心模块

```
app/src/main/java/com/xmov/metahuman/app/
├── trpg/                          # TRPG核心逻辑
│   ├── StoryTree.kt               # 剧情树数据结构
│   ├── GameEngine.kt              # DM Orchestrator游戏引擎
│   ├── RoomManager.kt             # 多人房间管理
│   └── ScriptParser.kt            # 剧本解析器
├── MenuActivity.kt                # 主菜单
├── RoomCreationActivity.kt         # 房间创建
├── JoinRoomActivity.kt            # 加入房间
├── GameActivity.kt                # 游戏主界面
├── RoomAdapter.kt                 # 房间列表适配器
└── MainActivity.kt                # 原SDK测试（保留）
```

### 数据模型

#### 剧情树 (StoryTree)
- **节点 (StoryNode)**: 场景描述、目标、允许动作、线索池、骰子规则
- **边 (TransitionEdge)**: 转场条件（线索/骰点/决策/输入）
- **Fail-Safe策略**: 自动纠偏机制

#### 游戏状态 (GameState)
- 当前场景ID
- 玩家列表
- 已发放线索
- 骰点历史
- 场景访问记录

#### 游戏复盘 (GameReview)
- 玩家表现统计
- 剧情路径
- 关键决策
- 线索分发
- 骰点统计

## 使用指南

### 1. 配置SDK

首次使用需配置Xmov数字人SDK：

```
1. 打开 SettingsActivity
2. 输入 AppId 和 AppSecret
3. 点击"开始"进入主菜单
```

### 2. 创建房间

```
1. 在主菜单选择游戏模式（剧本杀/跑团/海龟汤）
2. 输入房间名称、最大人数、密码（可选）
3. 上传剧本文件（JSON/TXT/ZIP格式）
4. 点击"创建房间"
```

### 3. 剧本格式

#### JSON格式（推荐）

```json
{
  "id": "剧本ID",
  "title": "剧本标题",
  "gameType": "JUBENSHA",
  "rootNodeId": "scene_001",
  "nodes": [
    {
      "id": "scene_001",
      "description": "场景标题",
      "narrativeText": "DM朗读的描述文本",
      "objectives": [],
      "allowedActions": ["SEARCH", "TALK", "DICE_ROLL"],
      "cluePool": {
        "publicClues": [],
        "privateClues": []
      },
      "diceRules": {
        "ruleType": "D20_SYSTEM",
        "diceType": "D20",
        "difficulty": 10,
        "successThreshold": 11
      },
      "imageUrl": "场景插画URL",
      "isCritical": false
    }
  ],
  "edges": [
    {
      "id": "edge_001",
      "fromNodeId": "scene_001",
      "toNodeId": "scene_002",
      "condition": {
        "type": "CLUE_FOUND",
        "requiredClues": ["clue_001"]
      },
      "priority": 1,
      "isFailSafe": false
    }
  ],
  "globalClues": [],
  "failSafePolicies": []
}
```

#### TXT格式（简化版）

```
# 场景一：午夜的古堡
这是场景一的描述文本...

# 场景二：古堡大厅
这是场景二的描述文本...
```

### 4. 游戏操作

- **搜查**: 在当前场景寻找线索
- **对话**: 输入文本与DM交互
- **调查**: 深入调查场景细节（可能需要骰点）
- **骰点**: 投掷骰子进行判定
- **线索卡**: 查看已获得的线索
- **玩家**: 查看房间成员
- **结束**: 房主可结束游戏并生成复盘

### 5. 复盘报告

游戏结束后自动生成复盘报告，包含：

- 玩家表现（访问场景数、收集线索数、骰点成功率）
- 剧情路径（时间轴）
- 关键决策记录
- 线索分发记录
- 骰点统计（成功率、大成功/大失败次数）

## 示例剧本

项目包含三个示例剧本：

1. **sample_jubensha.json** - 《午夜凶铃》
   - 剧本杀模式
   - 搜查、对话、调查
   - 线索推进剧情

2. **sample_paotuan.json** - 《龙之宝藏》
   - 跑团模式
   - D20骰点系统
   - 战斗与冒险

3. **sample_haitang.json** - 《神秘的晚餐》
   - 海龟汤模式
   - 提问推理
   - 逐步揭示真相

## 依赖项

```gradle
// 核心SDK
implementation files("libs/xmovdigitalhuman-v0.0.1.aar")

// TRPG功能
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
implementation("androidx.cardview:cardview:1.0.0")

// 协程支持
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

// 其他依赖
implementation("com.google.code.gson:gson:2.13.1")
implementation("com.squareup.okhttp3:okhttp:5.1.0")
implementation("io.socket:socket.io-client:2.1.0")
```

## 后续扩展

### 待实现功能

1. **网络多人同步**
   - 使用WebSocket或Socket.IO
   - 实时同步游戏状态
   - 支持跨设备游戏

2. **图像生成API集成**
   - 场景插画自动生成
   - 线索卡图片生成
   - 角色形象生成

3. **Agent服务端**
   - 剧本智能解析
   - 剧情树自动生成
   - Fail-Safe策略自动生成

4. **高级功能**
   - 角色创建系统
   - 物品系统
   - 技能系统
   - 成就系统

5. **社交功能**
   - 好友系统
   - 房间收藏
   - 游戏记录
   - 分享复盘

## 注意事项

1. **SDK配置**: 必须配置有效的AppId和AppSecret才能使用数字人功能
2. **剧本格式**: 建议使用JSON格式以获得最佳体验
3. **Fail-Safe**: 合理设置Fail-Safe策略可防止玩家偏离剧情
4. **线索设计**: 合理分配公开/私密线索，平衡游戏难度
5. **骰点规则**: 根据游戏类型选择合适的骰子系统

## 开发者

基于XmovLiteAvatar Android Demo改造
版本: v1.0.0

## 许可证

基于原项目许可证
