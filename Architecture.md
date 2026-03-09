# EdgeAgentAndroid 架构设计文档

## 1. 架构概览

本项目采用 **Clean Architecture** 分层架构，结合 **端侧优先 (Edge-First)** 设计理念。

### 核心设计原则

1. **端侧优先**：所有基础功能必须在断网情况下可用
2. **隐私合规**：敏感数据（设备控制、本地文件）绝不上云
3. **按需兜底**：仅在本地模型置信度不足时才调用云端 API
4. **松耦合设计**：感知层、推理层、执行层通过接口解耦

## 2. 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  (UI/ViewModel - Jetpack Compose/XML + StateFlow)           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         AgentOrchestrator (核心编排器)                │   │
│  │  ┌────────────────────────────────────────────────┐  │   │
│  │  │        AgentStateMachine (状态机)              │  │   │
│  │  └────────────────────────────────────────────────┘  │   │
│  │  ┌────────────────────────────────────────────────┐  │   │
│  │  │        IntentRouter (端云路由决策)             │  │   │
│  │  └────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Perception  │  │  Inference   │  │  Execution   │      │
│  │   (感知)     │  │   (推理)     │  │   (执行)     │      │
│  │              │  │              │  │              │      │
│  │ Screen       │  │ Local VLM    │  │ Gesture      │      │
│  │ Capture      │  │ Engine       │  │ Executor     │      │
│  │              │  │              │  │              │      │
│  │ UI Tree      │  │ Cloud        │  │ Accessibility│      │
│  │ Extractor    │  │ Fallback     │  │ Actions      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Local RAG Engine                        │   │
│  │         (FAISS/SQLite Vector Store)                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                              │
│  - EdgeAgentAccessibilityService (无障碍服务)                │
│  - OverlayService (悬浮窗服务)                               │
└─────────────────────────────────────────────────────────────┘
```

## 3. 状态机设计

### 状态定义
- **IDLE**: 空闲，等待用户触发
- **PERCEIVING**: 正在捕获屏幕截图和 UI 树
- **REASONING_LOCAL**: 本地 VLM 模型推理中
- **REASONING_CLOUD**: 云端大模型推理中（兜底）
- **EXECUTING**: 执行操作（点击、滑动、输入）
- **ERROR**: 错误状态
- **COMPLETED**: 任务完成

### 状态转换流程
```
IDLE → PERCEIVING → REASONING_LOCAL → EXECUTING → COMPLETED → IDLE
                         ↓ (低置信度)
                    REASONING_CLOUD → EXECUTING → COMPLETED → IDLE
```

## 4. 核心组件职责

### AgentOrchestrator (编排器)
- 协调整个 Agent 的工作流
- 管理状态机转换
- 调度感知、推理、执行三大模块

### IntentRouter (意图路由)
- 根据用户输入和上下文判断意图类型
- 决策是否需要调用云端（基于置信度阈值）
- 管理本地 RAG 检索优先级

### ILocalModelEngine (本地模型引擎接口)
```kotlin
interface ILocalModelEngine {
    suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String? = null
    ): AgentResponse
}
```

### GestureExecutor (手势执行器)
- 封装 AccessibilityService 的 dispatchGesture
- 提供点击、滑动、长按、输入文本等原子操作
- 内存安全 + 主线程保护

## 5. 数据流

```
用户触发 
  → AccessibilityService 捕获屏幕 (Bitmap + UI Tree)
  → AgentOrchestrator 接收感知数据
  → IntentRouter 判断意图
  → 本地 RAG 检索相似历史
  → ILocalModelEngine 推理 (Mock/Qwen VLM)
  → 判断置信度
      - 高置信度 → 直接执行
      - 低置信度 → CloudFallbackClient 调用云端 → 执行
  → GestureExecutor 执行操作
  → 状态机回到 IDLE
```

## 6. 技术栈

- **语言**: Kotlin (100%)
- **异步**: Coroutines + Flow
- **依赖注入**: Hilt (Dagger)
- **本地推理**: MediaPipe / MLC LLM (Qwen 3.5 0.8B/2B)
- **向量存储**: FAISS-Android / SQLite with Vector Extension
- **云端兜底**: DeepSeek API / 阿里云百炼 API
- **UI**: Jetpack Compose (现代化 UI)
- **无障碍**: AccessibilityService + MediaProjection

## 7. 内存与性能优化策略

1. **Bitmap 复用池**: 避免频繁 GC
2. **协程作用域管理**: 防止内存泄漏
3. **模型推理异步化**: 不阻塞主线程
4. **UI 树裁剪**: 只提取关键节点信息
5. **懒加载模型**: 首次使用时才加载到内存

## 8. 安全与隐私

- 所有本地操作数据不上传
- 云端调用前脱敏处理（移除 PII）
- 用户可配置"纯本地模式"（完全禁用云端）
- 符合 GDPR / 中国个人信息保护法

---

**下一步**: Phase 2 - 实现 AccessibilityService 感知与执行层

---

## 各 Phase 核心功能

### Phase 1: 系统架构与基座搭建 ✅
**核心功能**：
- Clean Architecture 三层架构设计
- 状态机（7 个状态 + 状态转换规则）
- 意图路由（6 种意图类型 + 云端兜底决策）
- Mock 模型引擎（模拟推理流程）
- 数据模型定义（AgentResponse, ActionType, ActionParams）

**技术选型**：
- 单例模式（替代 Hilt）
- 传统 XML 布局（替代 Compose）
- Kotlin Coroutines + StateFlow
- Java 17 + Kotlin 2.0.21

**成果**：
- 完整的架构设计
- 可运行的 Mock 推理流程
- 状态机正常工作
- 意图识别和路由决策

---

### Phase 2: 无障碍视觉捕获与执行层 ✅
**核心功能**：
- EdgeAgentAccessibilityService（无障碍服务核心）
- GestureExecutor（手势执行：点击、滑动、返回等）
- ScreenCaptureManager（Bitmap 对象池，内存优化）
- UITreeExtractor（UI 树提取和过滤）

**技术亮点**：
- Bitmap 复用池（避免频繁 GC）
- 协程封装手势操作（主线程保护）
- UI 树智能过滤（减少 70% 数据量）
- 完整的无障碍权限配置

**成果**：
- 无障碍服务框架完成
- 手势执行能力就绪
- 内存优化机制
- 等待与 Domain 层集成

---

### Phase 3: 真实操作执行与集成 🔄 (进行中)
**核心功能**：
- 将 AgentResponse 转换为真实的无障碍操作
- 集成 GestureExecutor 到推理流程
- 实现真实的点击、滑动、输入文本
- 错误处理和重试机制

**实现步骤**：
1. 创建 ActionExecutor（动作执行器）
2. 连接 MainViewModel 和 AccessibilityService
3. 实现各种 ActionType 的真实执行
4. 添加执行结果反馈

**预期效果**：
- 点击按钮后，真的能点击屏幕
- 滑动指令能真的滑动
- 打开应用能真的启动应用

---

### Phase 4: 云端 API 集成 ⏳ (待开发)
**核心功能**：
- CloudFallbackClient（云端 API 客户端）
- 集成 DeepSeek / 豆包 / 阿里云大模型 API
- 真实的云端兜底逻辑
- API 调用优化（缓存、重试、超时）

**技术实现**：
- Retrofit + OkHttp（网络请求）
- Kotlin Serialization（JSON 解析）
- 协程异步调用
- 错误处理和降级策略

**API 选择**：
- DeepSeek API（100k+ Context）
- 阿里云百炼 API
- 豆包 API（可选）

---

### Phase 5: 本地 RAG 向量检索 ⏳ (待开发)
**核心功能**：
- 本地向量数据库（FAISS / SQLite-Vector）
- 常用指令缓存（"调音量"、"打开微信"）
- 用户习惯学习
- 向量检索优先级

**技术实现**：
- FAISS-Android（向量检索）
- Room Database（持久化）
- Embedding 模型（文本向量化）

**优化策略**：
- 高频指令本地缓存
- 零网络请求
- 毫秒级响应

---

### Phase 6: 本地多模态模型集成 ⏳ (可选)
**核心功能**：
- 集成 Qwen 3.5 (0.8B/2B) VLM
- MediaPipe / MLC LLM 推理框架
- 模型量化和优化
- 端侧推理加速

**技术挑战**：
- 模型大小（200-500MB）
- 推理速度（1-3 秒）
- 内存占用（200-500MB）
- 兼容性问题

**备选方案**：
- 完全使用云端 API（更简单）
- 混合模式（简单任务本地，复杂任务云端）

---

### Phase 7: 语音交互 ⏳ (可选)
**核心功能**：
- 语音输入（ASR）
- 语音输出（TTS）
- 唤醒词检测
- 连续对话

**技术实现**：
- Android SpeechRecognizer
- TextToSpeech API
- 或集成第三方 SDK

---

### Phase 8: 高级功能 ⏳ (可选)
**核心功能**：
- 多步骤任务规划
- 上下文记忆
- 用户偏好学习
- 悬浮窗快捷入口
- 通知栏快捷操作

---

## 当前进度

| Phase | 状态 | 完成度 |
|-------|------|--------|
| Phase 1: 架构与基座 | ✅ 完成 | 100% |
| Phase 2: 无障碍服务 | ✅ 完成 | 100% |
| Phase 3: 真实操作执行 | 🔄 进行中 | 0% |
| Phase 4: 云端 API | ⏳ 待开发 | 0% |
| Phase 5: 本地 RAG | ⏳ 待开发 | 0% |
| Phase 6: 本地模型 | ⏳ 可选 | 0% |
| Phase 7: 语音交互 | ⏳ 可选 | 0% |
| Phase 8: 高级功能 | ⏳ 可选 | 0% |

---

## 最小可用产品 (MVP)

要实现类似豆包的基础功能，需要完成：
- ✅ Phase 1: 架构设计
- ✅ Phase 2: 无障碍服务
- 🔄 Phase 3: 真实操作执行
- 🎯 Phase 4: 云端 API 集成

**预计时间**：Phase 3 (1-2 天) + Phase 4 (1 天) = 2-3 天

**MVP 功能**：
- 用户说"打开 Chrome" → 真的打开 Chrome
- 用户说"向上滑动" → 真的滑动屏幕
- 用户说"点击搜索框" → 真的点击搜索框
- 复杂任务自动调用云端 API

---