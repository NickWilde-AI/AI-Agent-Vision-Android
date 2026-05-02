# AI 开发指南 (AI Development Guide)

> **重要说明**：本文档专门为 AI 编程助手（Claude、GPT、Cursor、Antigravity 等）准备，包含项目开发的所有规范、要求和上下文信息。
> 
> **使用方法**：当开启新的对话或切换 AI 工具时，请先阅读本文档，快速了解项目状态和开发规范。

---

## 📋 项目基本信息

### 项目名称
- **显示名称**：VisionAgent Android
- **应用名称**：VisionAgent
- **包名**：`com.tencent.edgeagent`（保持不变，避免大规模重构）
- **GitHub 仓库**：https://github.com/NickWilde-AI/AI-Agent-Vision-Android

### 项目定位
这是一个面向 **2026 年 AI 大厂面试** 的顶级项目作品集，展示端侧 AI 工程落地能力和移动端架构设计水平。

### 核心特性
- 🚀 端侧优先：断网场景 100% 基础可用
- 🔒 隐私合规：敏感操作绝不上云
- ☁️ 智能兜底：低置信度自动调用云端 API
- 🎯 真实操作：基于 AccessibilityService 实现屏幕自动化

---

## 🏗️ 技术架构

### 架构模式
- **Clean Architecture** 三层架构
- **状态机驱动**：7 个状态（IDLE、PERCEIVING、REASONING_LOCAL、REASONING_CLOUD、EXECUTING、COMPLETED、ERROR）
- **意图路由**：6 种意图类型，智能云端决策

### 技术栈
- **语言**：Kotlin 2.0.21 (100%)
- **构建工具**：Gradle 8.9.0
- **JDK 版本**：Java 17
- **最低 SDK**：API 24 (Android 7.0)
- **目标 SDK**：API 35
- **架构模式**：单例模式（不使用 Hilt）
- **UI 框架**：传统 XML 布局 + ViewBinding（不使用 Compose）
- **异步处理**：Kotlin Coroutines + Flow
- **日志库**：Timber 5.0.1

### 为什么不用 Hilt 和 Compose？
- **Hilt**：与 Kotlin 2.0 + Java 17 存在 JavaPoet 版本冲突，选择更稳定的单例模式
- **Compose**：降低项目复杂度，使用传统 XML 布局更易维护。**严格禁止使用 Compose**

### 开发语言
- 优先使用 Kotlin，但不强制
- 如果遇到 Kotlin 版本与其他依赖冲突，可以灵活使用 Java
- 以项目稳定性和兼容性为优先

---

## 📂 项目结构

```
app/src/main/java/com/tencent/edgeagent/
├── domain/                          # Domain Layer (业务逻辑)
│   ├── agent/
│   │   ├── AgentStateMachine.kt     # 状态机
│   │   └── IntentRouter.kt          # 意图路由
│   └── model/
│       ├── AgentState.kt            # 状态枚举
│       ├── AgentIntent.kt           # 意图模型
│       └── AgentResponse.kt         # 响应模型
│
├── data/                            # Data Layer (数据/推理层)
│   ├── inference/
│   │   ├── ILocalModelEngine.kt     # 模型引擎接口
│   │   └── MockModelEngine.kt       # Mock 实现
│   ├── perception/
│   │   ├── ScreenCaptureManager.kt  # 截图管理（Bitmap 对象池）
│   │   └── UITreeExtractor.kt       # UI 树提取
│   ├── execution/
│   │   ├── GestureExecutor.kt       # 手势执行
│   │   └── ActionExecutor.kt        # 动作执行器
│   └── cloud/
│       ├── CloudConfig.kt           # 云端配置
│       ├── ICloudClient.kt          # 云端客户端接口
│       ├── DeepSeekClient.kt        # DeepSeek API 实现
│       └── CloudFallbackManager.kt  # 云端兜底管理器
│
├── ui/                              # Presentation Layer (UI)
│   ├── MainActivity.kt              # 主界面
│   └── MainViewModel.kt             # ViewModel
│
└── service/                         # Service Layer
    └── EdgeAgentAccessibilityService.kt  # 无障碍服务
```

---

## 🎯 开发规范

### Git 提交规范

**重要**：所有 Git Commit 必须使用中文，描述精简、逻辑清晰。

**推荐格式（概要 + `- ` 要点列表）**：改动较多时使用，便于对照审查；单行概要 + 每条 `- ` 一事，条目数量按需控制（常见 3～6 条）。

```
<type>: 单行概要（中文）

- 第一点改动说明
- 第二点改动说明
```

- `<type>` 建议使用约定前缀：`feat`（新能力）、`fix`（修复）、`refactor`（重构）、`chore`（杂项）等。
- 第一段概要提纲挈领；正文列表对应代码里的模块或行为变化，避免堆砌长篇段落。

**写入要点列表**：多条 `-m` 会在段落之间插入空行，列表不易紧凑；推荐把完整说明写入文件后用 `-F`，或直接 `git commit` 打开编辑器按上文格式书写。

```bash
# 例如 commit-msg.txt 内容为「概要 + 空行 + 若干 - 要点」：
git commit -F commit-msg.txt
```

**简单改动**：仍可使用单行说明。
```bash
git commit -m "新增本地模型推理引擎接口"
git commit -m "修复屏幕截图权限问题"
git commit -m "优化 Agent 状态机逻辑"
git commit -m "将项目名称从 EdgeAgent 改为 VisionAgent"
```

**错误示例**：
```bash
git commit -m "first commit"           # ❌ 使用英文
git commit -m "update files"           # ❌ 描述不清晰
git commit -m "fix bug"                # ❌ 没有说明修复了什么
```

### GitHub 上传规则

**⚠️ 重要**：只有当用户**明确要求上传到 GitHub** 时，才能执行 `git push` 操作。

**正确流程**：
1. 用户说："上传到 GitHub" / "提交到 GitHub" / "push 到 GitHub"
2. AI 执行：`git add` → `git commit` → `git push`

**错误做法**：
- ❌ 自动上传代码
- ❌ 在用户没有明确要求时执行 push
- ❌ 假设用户想要上传

### 自动运行与调试规范 ⚠️ 必须遵守

**核心原则**：每次完成代码修改后，必须立即运行 Gradle 构建/安装检查，模拟 Android Studio 的开发流程。

**本机 Android SDK 路径**：

```text
/Users/chenpeng/Library/Android/sdk
```

**本地配置要求**：项目根目录 `local.properties` 必须包含：

```properties
sdk.dir=/Users/chenpeng/Library/Android/sdk
```

`local.properties` 已被 `.gitignore` 忽略，允许存放本机 SDK 路径和本地 API Key，禁止提交到 Git。

**标准流程**：
1. **修改代码**：完成功能开发或 bug 修复
2. **立即构建并安装**：执行 `./gradlew installDebug`
3. **清空日志**：执行 `adb logcat -c`（在安装后立即清空，确保只看到新的日志）
4. **检查构建结果**：
   - 如果构建失败，查看错误信息并立即修复
   - 如果构建成功，告知用户可以测试
5. **等待用户操作**：用户在手机上测试应用
6. **查看运行日志**：当用户要求查看日志时，执行 `./view_logs.sh "关键字"` 或 `adb logcat -d` 查看应用日志
7. **检查运行状态**：
   - 如果应用崩溃或报错，立即查看日志并修复
   - 如果应用正常运行，向用户报告成功
8. **循环修复**：如果有问题，修复后重复步骤 2-7

**命令示例**：
```bash
# 1. 确认 SDK 路径已写入 local.properties
printf 'sdk.dir=/Users/chenpeng/Library/Android/sdk\n' >> local.properties

# 2. 构建并安装
./gradlew installDebug

# 3. 立即清空日志
adb logcat -c

# 4. 用户测试时按关键字实时看日志
./view_logs.sh "AgentTask|DeepSeek|OPEN_APP|AndroidRuntime|ForegroundService"
```

**注意事项**：
- ❌ 不要在代码修改后跳过 Gradle 运行检查
- ✅ 每次代码修改后主动运行 `./gradlew installDebug`
- ✅ 如果只是文档改动，可以不运行 Android 构建，但需要说明原因
- ✅ 运行后立即清空日志，确保只看到新的日志
- ✅ 发现错误立即修复，不要反复要求用户手动排查
- ✅ 修复后重新运行，确保问题解决
- ✅ 最多尝试 3 次修复，如果仍失败则向用户说明情况

### 代码风格

1. **开发语言**：优先使用 Kotlin，但不强制。如果遇到版本兼容问题，可以使用 Java
   - 当前项目主要使用 Kotlin
   - 如果 Kotlin 版本与其他依赖冲突，可以视情况使用 Java
   - 灵活选择，以项目稳定性为优先
2. **单例模式**：所有管理类使用双重检查锁单例模式
3. **协程优先**：异步操作优先使用 Kotlin Coroutines（如果使用 Kotlin）
4. **日志规范**：使用 Timber，不使用 Log
5. **命名规范**：
   - 类名：大驼峰（PascalCase）
   - 函数/变量：小驼峰（camelCase）
   - 常量：全大写下划线（UPPER_SNAKE_CASE）

---

## 📊 开发进度

| Phase | 功能 | 状态 | 完成度 |
|-------|------|------|--------|
| Phase 1 | 架构与基座搭建 | ✅ 完成 | 100% |
| Phase 2 | 无障碍服务 | ✅ 完成 | 100% |
| Phase 3 | 真实操作执行 | ✅ 完成 | 90% |
| Phase 4 | 云端 API 集成 | ✅ 完成 | 100% |
| Phase 4.5 | 多轮对话与反馈循环 | 🚧 进行中 | 85% |
| Phase 5 | 本地 RAG | ⏳ 待开发 | 0% |
| Phase 6 | 本地 VLM | ⏳ 可选 | 0% |
| Phase 7 | 语音交互 | ⏳ 可选 | 0% |

### 已实现功能 ✅
- 状态机系统（7 个状态）
- 意图路由（6 种意图类型）
- Mock 推理引擎
- 无障碍服务（屏幕捕获、UI 树提取）
- 手势执行（点击、滑动、返回、Home）
- 动作执行器（真实无障碍点击）
- 云端 API 集成（阿里云百炼 Qwen-VL-Max）
- Bitmap 对象池（内存优化）
- **MainActivity 完整实现**（权限状态、指令输入、状态展示）
- **真实屏幕截图接入**（MediaProjection 授权流程，降级 UI 树模式）
- **文本输入实现**（ACTION_SET_TEXT + 剪贴板降级）
- **executeOpenApp 优先 Intent 启动**（更可靠，图标查找作备选）
- 自定义指令输入框
- 多轮对话框架（AgentExecutor）
- **多轮对话提示词优化**（含 UI 树摘要、当前包名、决策规则）
- WAIT 操作支持
- 重复操作检测
- 编译时间显示
- 深色科技感 UI 主题（#0D0F14 背景 + #00E5FF accent）

### 进行中功能 🚧
- **多轮对话系统**：支持 LLM 多次交互 ✅
- **视觉反馈循环**：执行 → 截图 → 验证 → 继续 ⚠️（截图功能缺失）
- **坐标缩放转换**：适配不同屏幕尺寸
- **操作验证机制**：检查操作是否成功
- **智能重试**：失败后自动重试 ✅

### 待实现功能 ⏳
- 输入文本（使用剪贴板或 AccessibilityNodeInfo）
- 打开应用（已实现基础功能，需优化）
- 设备控制（音量、亮度调节）
- 本地 RAG（FAISS/SQLite 向量检索）
- 本地 VLM（Qwen 3.5 集成）
- 语音交互（ASR + TTS）

### 当前问题 ⚠️
1. **屏幕截图**：已接入 MediaProjection，需用户在 MainActivity 点击「去授权」完成授权流程；未授权时降级为纯 UI 树模式（多轮对话不中断）
2. **无障碍服务权限**：rootInActiveWindow 有时为 null，已加重试机制，影响可控
3. **坐标精度**：真实截图授权后才能验证云端返回坐标准确性

### 技术难点（已解决）
- **屏幕截图**：✅ MediaProjection 已实现，MainActivity 提供授权入口，降级策略保证多轮对话不中断
- **多轮对话死循环**：✅ 提示词包含 UI 树摘要 + 当前包名，LLM 可判断应用状态；重复操作检测防止死循环
- **打开应用**：✅ 优先 `Intent.getLaunchIntentForPackage`（100% 可靠），备选无障碍图标点击
- **文本输入**：✅ `ACTION_SET_TEXT` + 剪贴板降级两级策略

---

## 📚 重要文档

说明文档主要集中在 **`docs/`**；根目录保留 **`README.md`**（门面）与本 **`AI_DEVELOPMENT_GUIDE.md`**（协作规范）。

### 必读
1. **README.md** — 项目总览与文档导航表
2. **docs/README.md** — 文档索引一览
3. **docs/GETTING_STARTED.md** — 快速上手 + 详细测试（合并原 QUICKSTART / TESTING_GUIDE）
4. **docs/ARCHITECTURE.md** — 完整架构说明

### Phase 总结（均在 `docs/`）
- **docs/PHASE1_SUMMARY.md** — Phase 1：架构基座、状态机与意图路由
- **docs/PHASE2_SUMMARY.md** — Phase 2：无障碍视觉捕获与执行层
- **docs/PHASE3_SUMMARY.md** — Phase 3：真实操作执行与集成
- **docs/PHASE4_SUMMARY.md** — Phase 4：云端 API 集成
- **docs/PHASE5_SUMMARY.md** — Phase 5：云端优先全无障碍 AI Agent
- **docs/API_EXAMPLES.md** — 云端 API 示例
- **docs/AI_BOOTSTRAP_PROMPT.md** — 从零搭建用的 AI Prompt（原 ProductRequirements.md）

---

## 🔧 常用命令

### 构建和安装
```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 清理构建
./gradlew clean
```

### Git 操作
```bash
# 查看状态
git status

# 添加文件
git add <file>

# 提交（使用中文）
git commit -m "描述修改内容"

# 推送到 GitHub（仅在用户明确要求时）
git push origin main
```

### 日志查看 ⚠️ 重要规范

**核心原则**：在每次运行 `./gradlew installDebug` 后立即清空日志，确保只看到新的运行日志。

**标准流程**：
```bash
# 1. 构建并安装应用
./gradlew installDebug

# 2. 立即清空日志（重要！）
adb logcat -c

# 3. 等待用户在手机上测试应用

# 4. 当用户要求查看日志时，查看历史日志
adb logcat -d | grep -E "VisionAgent|EdgeAgent|MainActivity|AndroidRuntime|FATAL|AgentStateMachine|MockModelEngine|ActionExecutor" | tail -2000
```

**注意事项**：
- ✅ 每次运行应用后立即清空日志（`adb logcat -c`）
- ✅ 用户要求查看日志时，使用 `adb logcat -d` 查看历史日志
- ✅ 这样可以确保只看到当前运行的日志，避免混淆
- ❌ 不要在用户要求查看日志时才清空日志
- ❌ 不要使用实时监控（`adb logcat`），会阻塞终端

**其他日志命令**：
```bash
# 查看完整历史日志
adb logcat -d

# 查看特定应用的日志
adb logcat -d | grep EdgeAgent

# 清空日志
adb logcat -c
```

---

## 🎯 核心设计原则

### 1. 端侧优先 (Edge-First)
- 所有基础功能必须在断网情况下可用
- 本地推理优先，云端仅作兜底

### 2. 隐私合规
- 设备控制（音量、亮度）：100% 本地，绝不上云
- 文本输入：隐私保护，不上云
- 应用操作：优先本地，低置信度云端兜底

### 3. 智能路由
- 置信度 ≥ 0.75：直接执行本地结果
- 置信度 < 0.75：调用云端 API
- 复杂推理任务：直接云端

### 4. 状态机驱动
- 明确的状态转换规则
- 防止非法状态转换
- 完整的日志记录

---

## 🚨 注意事项

### 1. 不要修改包名
包名保持 `com.tencent.edgeagent`，避免大规模重构。只修改显示名称。

### 2. 不要引入 Hilt
项目使用单例模式，不要尝试引入 Hilt 或其他依赖注入框架（版本兼容问题）。

### 3. 不要使用 Compose ⚠️ 硬性要求
项目使用传统 XML 布局，**严格禁止**引入 Jetpack Compose。这是不可妥协的硬性要求。

### 4. 开发语言灵活选择
- 优先使用 Kotlin，但不强制
- 如果遇到 Kotlin 版本与其他依赖冲突，可以使用 Java
- 以项目稳定性和兼容性为优先

### 5. 保持 Java 17
不要升级到 Java 21 或更高版本，保持 Java 17。

### 5. 云端 API 配置
DeepSeek API Key 配置在 `CloudConfig.kt`，不要硬编码在其他地方。

---

## 💡 面试亮点

### 技术亮点
1. **端侧 AI 工程落地**：端云协同架构，本地优先 + 云端兜底
2. **架构设计能力**：Clean Architecture + 状态机 + 意图路由
3. **隐私合规意识**：架构层面的隐私保护，不是事后补救
4. **性能优化能力**：Bitmap 对象池，避免频繁 GC
5. **工程化思维**：Mock 先行，快速验证数据流

### 面试问答
**Q1**: 如何设计端云协同架构？  
**A**: 本地推理优先，置信度 < 0.75 自动调用云端 API，云端失败自动降级到本地。

**Q2**: 为什么不用 Hilt？  
**A**: Hilt 与 Kotlin 2.0 + Java 17 存在版本冲突，选择更稳定的单例模式。

**Q3**: 如何保证隐私合规？  
**A**: 在 IntentRouter 中为每种意图设置 `allowCloudFallback` 标志，设备控制和文本输入绝不上云。

**Q4**: 如何优化内存？  
**A**: 实现 Bitmap 对象池，使用 ConcurrentLinkedQueue 管理最多 3 个 Bitmap，避免频繁 GC。

---

## 📝 用户特殊要求

### 1. Git Commit 规范
- ✅ 必须使用中文
- ✅ 描述精简、逻辑清晰
- ❌ 不使用英文
- ❌ 不使用模糊描述

### 2. GitHub 上传规则
- ✅ 只有用户明确要求时才上传
- ❌ 不自动上传
- ❌ 不假设用户想要上传

### 3. 项目命名
- 显示名称：VisionAgent Android
- 应用名称：VisionAgent
- 包名：com.tencent.edgeagent（不变）

### 4. 开发语言
- 优先使用 Kotlin，但不强制
- 如果遇到版本兼容问题，可以使用 Java
- 以项目稳定性为优先
- **Compose 严格禁止**：不可使用 Jetpack Compose

### 5. 自动运行与调试 ⚠️ 重要
- **每次完成代码修改后，必须立即运行应用**
- 模拟 Android Studio 的开发流程：修改代码 → 构建 → 安装 → 运行
- 如果启动失败或有报错，立即查看日志并修复
- 不要等待用户要求，主动执行 `./gradlew installDebug`
- 运行后检查 logcat 日志，确认应用正常工作
- 如果发现错误，立即修复并重新运行

---

## 🔄 快速上手流程

当你作为新的 AI 助手接手这个项目时，请按以下步骤操作：

1. **阅读本文档**：了解项目基本信息和开发规范
2. **阅读 README.md**：了解项目功能和架构
3. **阅读 docs/ARCHITECTURE.md**：深入了解架构设计
4. **按需阅读 docs/PHASE1_SUMMARY.md～PHASE5_SUMMARY.md**：对照各阶段实现与设计脉络
5. **询问用户需求**：明确用户想要开发什么功能
6. **开始编码**：遵循本文档的所有规范

---

## 📞 联系信息

- **GitHub 仓库**：https://github.com/NickWilde-AI/AI-Agent-Vision-Android
- **项目类型**：个人作品集项目
- **目标**：AI 大厂面试

---

## 🔖 版本历史

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-03-09 | v1.0 | 初始版本，包含基本开发规范 |
| 2026-03-11 | v1.1 | MainActivity 完整实现、真实截图接入、文本输入、多轮对话优化 |

---

**最后更新**：2026-03-11  
**维护者**：项目所有者

---

## 📌 快速检查清单

在开始开发前，请确认：

- [ ] 已阅读本文档
- [ ] 已了解项目架构（Clean Architecture + 状态机）
- [ ] 已了解技术栈（Kotlin 2.0.21 + Java 17）
- [ ] 已了解 Git 提交规范（中文 + 精简描述）
- [ ] 已了解 GitHub 上传规则（仅在用户要求时）
- [ ] 已了解不使用 Hilt 和 Compose 的原因
- [ ] 已了解项目命名（VisionAgent Android）

---

**祝开发顺利！如有疑问，请查阅相关文档或询问用户。**
