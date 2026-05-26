# VisionAgent 项目记忆

> 这个文件是项目的长期记忆文件，用来记录产品方向、仓库状态、设备环境、模型部署、关键决策、调试结果和后续任务。后续开发应优先读取本文件，避免重复确认已经完成的事情。

## 项目概览

- 项目名称：VisionAgent Android / AI-Agent-Vision-Android
- Android 包名：`com.tencent.edgeagent`
- 本地路径：`/Users/chenpeng/WorkSpace/文稿/Tencent/TencentCodeing/AI-Agent-Vision-Android`
- GitHub 仓库：`https://github.com/NickWilde-AI/AI-Agent-Vision-Android.git`
- 当前技术栈：Android 原生 Kotlin + XML 布局 + Gradle；Java 主要来自 Android/Gradle 运行环境，不是业务主体语言。
- 产品方向：真正的 Android Agent / Agent OS 层，不再定位为大厂面试 Demo。
- 核心形态：Android App 级 Agent，通过无障碍服务、屏幕截图、UI 树、RAG、App 专项策略、Trace 回放、本地模型和云端模型路由来完成手机自动化任务。
- 语言选型结论：手机端常驻 Agent 核心不建议整体改 Python。Android 权限、无障碍、MediaProjection、后台服务、端侧推理引擎都更适合 Kotlin/Java 层实现；Python 更适合做离线评测、Trace 分析、RAG 数据构建和开发脚本。

## 用户偏好

- 不接受把大模型部署在电脑端，然后通过 USB / ADB 控制手机作为核心产品方案。
- 可接受的推理路径：
  - 模型直接部署在 Android 手机本地运行。
  - 云端/API 模型由 Android App 直接调用。
- 目标体验：高度定制化的 Android Agent OS 层，能力级别接近手机 AI 助手，但必须形成本项目自己的产品体系。
- 测试机可以用于无线调试和真机实验。

## 当前测试设备

- 设备型号：Redmi K60 / `23013RK75C`
- 设备代号：`mondrian`
- Android 版本：13
- ADB 模式：无线 ADB
- 曾观察到的 ADB 序列号：`adb-b0da5aae-SgluOx._adb-tls-connect._tcp`
- 当前可用无线 ADB 地址：`192.168.10.166:39791`
- 存储情况：
  - 部署模型前观察到 `/sdcard` 约有 98GB 可用空间。
- ADB shell 可以通过 Wi-Fi 工作。
- 用户曾表示 `adb root` 可用；但本次实际执行 `adb root` 返回 `adbd cannot run as root in production builds`，所以模型部署使用 `run-as com.tencent.edgeagent` 完成。

## 模型与运行引擎

- 第一阶段本地模型目标：Gemma 4 E2B 指令模型，LiteRT-LM 格式。
- Hugging Face 仓库：`litert-community/gemma-4-E2B-it-litert-lm`
- 选用文件：`gemma-4-E2B-it.litertlm`
- 模型格式：`.litertlm`
- Android 端侧运行引擎：`com.google.ai.edge.litertlm:litertlm-android:0.12.0`
- Kotlin 工具链：接入 LiteRT-LM 后升级到 `2.3.21`
- 许可证：模型卡显示为 Apache-2.0。
- Hugging Face 头信息中的预期文件大小：`2,588,147,712` 字节。
- SHA-256：`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- 本地模型下载路径：`local_models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm`
- 手机最终模型路径：`/data/data/com.tencent.edgeagent/files/models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm`
- 手机临时中转路径：`/data/local/tmp/gemma-4-E2B-it.litertlm`
- 说明：LiteRT-LM 是 Google 官方端侧大模型运行引擎，不是 APK，也不是 Gemini。我们的 APK 是 `app-debug.apk`，它内置 LiteRT-LM 运行库，并读取手机里的 Gemma 模型文件。

## 端侧模型路线记录

- 当前产品主链路仍然是 Android App 内直接调用阿里云百炼 / Qwen-VL-Max。它负责复杂视觉理解、多轮页面判断和高不确定性场景。
- 当前本地模型 Gemma 4 E2B + LiteRT-LM 已在 Redmi K60 上完成健康检查，但它现阶段只作为端侧模型基座、离线兜底和后续本地推理储备，不作为复杂手机 Agent 的主力。
- 4bit / 8bit 是模型权重量化精度，不是普通压缩包。4bit 更省内存、更适合移动端，但效果和稳定性依赖量化方案；8bit 更稳但更吃内存。
- 当前 `.litertlm` 文件不是 GGUF，不能直接用 llama.cpp 的 `Q4_K_M`、`Q8_0` 工具原地量化。若要强控 4bit / 8bit，需要换成 GGUF + llama.cpp / Cactus，或寻找官方已导出的 LiteRT-LM 量化模型。
- `GGUF + llama.cpp`：模型生态大、量化选择多，适合做本地 LLM 性能实验；Android 工程集成、多模态输入和 GPU/NPU 加速需要额外适配。
- `Cactus`：更偏移动端 AI 推理 SDK，适合后续评估 GGUF、本地/云端混合推理和移动端部署体验。
- 阿里 GUI Agent 路线需要重点跟踪：`Mobile-Agent`、`GUI-Owl`、`GUI-Owl-1.5`。这条线更贴近手机/桌面/浏览器 GUI 自动化，对本项目的多 Agent、反思、记忆、回放和 App 策略库有直接参考价值。
- 现阶段判断：红米 K60 不是不可用设备，而是可用的低成本基线机。它适合验证 App 架构、权限、UI 树、截图、Trace、RAG 和轻量本地推理；不适合把复杂视觉 Agent 全部压到本地实时运行。
- 如果后续更换测试机，应优先考虑：16GB 以上内存、更新旗舰 SoC、稳定 GPU/NPU 推理支持、良好散热、可长期开发者模式调试，而不是只看跑分。

## 已实现能力

- 云端兜底模型已切换到阿里云 / Qwen-VL-Max，用于视觉能力和复杂场景兜底。
- 产品执行策略已调整：复杂视觉、多轮页面理解和 App 操作继续以阿里千问为云端主链路；设备控制、文本输入等本地优先场景由 `EdgeCloudRouter` 显式路由。
- 已新增 `EdgeCloudRouter`：根据意图、云端可用性、本地模型可用性和 L1 兜底能力，输出 `CLOUD_AGENT`、`LOCAL_SINGLE_ROUND` 或 `DETERMINISTIC_L1`，并写入 AgentTrace。
- 已新增 L1 安全兜底路由 `L1CommandRouter`：
  - 调高/调低音量。
  - 媒体静音/取消静音、播放暂停、上一首、下一首。
  - 调高/调低亮度。
  - Wi-Fi、蓝牙、飞行模式、网络、移动网络、热点、NFC、VPN 设置入口。
  - 显示、声音与触感、通知、定位、日期时间、语言、壁纸、无障碍、应用管理、电池、存储、隐私、安全、勿扰设置入口。
  - 返回、Home、最近任务、通知栏、快捷设置、关闭系统遮罩、锁屏、电源菜单、分屏。
  - 打开相机、微信、美团、支付宝、淘宝、抖音、QQ、电话、设置、浏览器。
  - Redmi K60 当前系统浏览器包名已确认为 `com.android.browser`；Chrome 单独映射到 `com.android.chrome`。
- 微信发消息不再作为第一验收主线；微信属于 L3 App 专项任务，最终发送属于 L4 高风险动作。
- 2026-05-24 后续决策：产品任务必须由 App 内 Agent / 模型优先决策。`L1CommandRouter` 只作为云端模型失败、本地模型不可用或不可观测状态下的低风险兜底，不再抢在模型前面执行任务。
- `AgentExecutor` 中实现了多轮任务执行、规划、反思和执行校验基础链路。
- `LocalRagEngine` 中实现了 RAG 策略记忆。
- RAG 数据已支持本地 JSONL 持久化。
- 已建立 App 专项策略注册机制：
  - 打开 App 策略：OPEN_APP 任务仍停留在 VisionAgent 主界面时，把模型对本 App 输入框/按钮的点击改写为系统级 `OPEN_APP`。
  - L1 设备控制策略：音量、亮度、Wi-Fi、蓝牙、飞行模式任务优先改写为 `DEVICE_CONTROL`。
  - L1 系统导航策略：返回、Home、最近任务、关闭键盘优先改写为 `BACK`、`HOME`、`RECENTS`。
  - 微信草稿状态机。
  - 浏览器策略。
  - 系统设置策略。
- `INPUT_TEXT` 执行成功后，会基于无障碍窗口状态尝试收起软键盘；如果模型点击了输入框导致键盘弹起，执行层会调用无障碍返回键关闭。
- `OPEN_APP` 到达目标包名后会立即判定任务达成，避免模型继续重复点击；开发验收模式下会记录收尾动作并尝试返回 VisionAgent 控制台。
- AgentTrace 已支持 JSONL 日志记录和最新会话回放。
- `view_logs.sh --replay` 已支持可读化回放最新 Trace，包括端云路由决策和本地模型诊断。
- 已基于无障碍 UI 树摘要建立本地视觉抽象层。
- 已接入 Gemma LiteRT-LM 本地模型链路：
  - `GemmaLiteRtModelEngine`
  - `LocalModelEngineProvider`
  - `AgentResponseJsonParser`
  - 本地运行时加载、推理或 JSON 解析失败时，安全回退到 `NO_ACTION`。
- 已增加“本地模型检查”按钮，用于执行一次有超时限制的本地 Gemma 推理烟测，并展示成功、失败、超时、耗时和原始响应；健康检查结果会写入 AgentTrace。
- App 启动时只检测本地模型是否就绪，不再强行加载 2.4GB 模型；完整运行时加载延迟到首次本地推理。
- 已增加无线部署辅助脚本：`deploy_device.sh`。
- 已增加模型推送脚本：`push_gemma_model.sh`。
- 已增加开发测试权限准备脚本：`dev_bootstrap_permissions.sh`。它只用于开启无障碍、设置调试 appops、尝试自动点击屏幕录制授权弹窗，不用于替 Agent 执行业务任务。
- 已增加开发测试常亮脚本：`keep_device_awake.sh`。它只用于防止 Redmi K60 长时间测试时熄屏，不读取页面、不点击业务控件、不替 Agent 执行任务。
- 已增加 L1 验收脚本：`l1_validation.sh`。它只负责单元测试、构建、安装、权限准备和启动 App；真正 L1 业务动作仍需要从 VisionAgent App 内执行。

## 文档结构

- `README.md`：项目门面、能力概览、快速开始。
- `PROJECT_MEMORY.md`：项目长期记忆、设备状态、当前任务线。
- `docs/PRODUCT.md`：产品定位、用户场景、版本规划、能力边界。
- `docs/L1_CAPABILITIES.md`：L1 基础能力清单、边界和 L2 入口。
- `docs/L1_TEST_MATRIX.md`：L1 真机验收矩阵、通过标准和边界回归。
- `docs/ARCHITECTURE.md`：当前真实架构、模块职责、主流程。
- `docs/DEVELOPMENT.md`：开发运行手册、API 示例、调试命令、AI 协作上下文。
- `docs/HISTORY.md`：Phase 1-6 历史记录和演进说明。

## 最近验证结果

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
  - 结果：通过。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`
  - 结果：接入 LiteRT-LM `0.12.0` 和 Kotlin `2.3.21` 后通过。
- `./gradlew :app:lintDebug`
  - 结果：接入 LiteRT-LM 后通过。
- 通过无线 ADB 向 Redmi K60 安装 Debug APK：
  - 命令：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - 结果：通过。
- App 启动日志检查：
  - 结果：通过。
  - 日志显示 App 使用 `Gemma LiteRT-LM engine`，并且完整模型运行时加载延迟到首次本地推理。
- 本地模型健康检查：
  - 结果：通过。
  - 日志显示 `[LocalGemma] LiteRT-LM model loaded`。
  - 日志显示 `Creating Gemma4DataProcessor`。
  - 推理结果来源：`LOCAL_VLM`。
  - 推理动作：`NO_ACTION`。
  - 置信度：`0.95`。
  - 推理耗时：`18,983ms`。
  - 模型输出了合法 JSON，并被 `AgentResponseJsonParser` 成功解析。

## 已修复的重要问题

- 修复微信策略误判：UI 树中看到“发送”按钮时，不再阻止所有点击。
- 修复微信策略误判：模型推理文本里提到 `send` 时，不再阻止输入框点击。
- 移除了错误声明的普通权限 `BIND_ACCESSIBILITY_SERVICE`；该权限只保留在无障碍服务声明上。
- 从基础主题中移除了 `windowLightNavigationBar`，以满足 minSdk 24 的 lint 检查。
- 增加真实 `org.json` 测试依赖，使 JVM 单元测试可以覆盖 JSON Trace / RAG 行为。
- 减少了 `AgentExecutor` 主循环中的强制非空断言。
- 将 Gradle Kotlin 配置从废弃的 `kotlinOptions.jvmTarget` 迁移到 `compilerOptions.jvmTarget`。
- 将 `/.kotlin/` 加入 `.gitignore`，避免 Kotlin 编译错误日志进入仓库。
- 将 `/local_models/` 加入 `.gitignore`，避免 2.4GB 模型文件进入仓库。

## 当前会话记录

- 2026-05-24：
  - 确认无线 ADB 设备在线：`23013RK75C / mondrian / Android 13`。
  - 确认可以通过 Wi-Fi 执行 ADB shell。
  - 确认手机 `/sdcard` 空间足够放置 2.59GB 模型。
  - 确认本地 Mac 空间足够下载模型。
  - 确认 Hugging Face 仓库 `litert-community/gemma-4-E2B-it-litert-lm` 是公开且无需门禁的。
  - 开始下载 `gemma-4-E2B-it.litertlm` 到 `local_models/gemma-4-e2b-it/`。
  - 在 `.gitignore` 中加入 `local_models/`，避免提交大模型二进制文件。
  - 新增 `push_gemma_model.sh`，用于把校验后的模型推送到 App 私有文件目录。
  - 完成 `gemma-4-E2B-it.litertlm` 下载。
  - 校验本地文件大小：`2,588,147,712` 字节。
  - 校验 SHA-256：`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`。
  - 通过无线 ADB 将模型推送到 Redmi K60。
  - 第一次放到外部 App 专属目录时，shell 可读，但 App 未能检测到，原因是中间目录归属为 shell。
  - 通过 `/data/local/tmp` 中转，并使用 `run-as com.tencent.edgeagent` 将模型复制到 App 私有目录。
  - 校验 App 私有文件所有者：`u0_a252:u0_a252`。
  - 校验手机端私有文件大小：`2,588,147,712` 字节。
  - 新增 `LocalModelManager`，使 Android App 可以检测已部署的 Gemma 4 E2B 模型文件。
  - 重新构建并通过无线 ADB 安装 App。
  - 验证 logcat 显示：`ModelInfo(name=Gemma 4 E2B detected, version=litert-lm-ready-runtime-pending, sizeInMB=2468.25, supportsMultimodal=true, avgInferenceTimeMs=0)`。
  - 重新执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`，构建、单元测试、APK 打包和 lint 均通过。
  - 重新安装最新 Debug APK，并启动 `com.tencent.edgeagent/.ui.MainActivity`。
  - 再次确认 logcat 报告 `Gemma 4 E2B detected`。
  - 增加 LiteRT-LM Android 运行时依赖，并实现 `GemmaLiteRtModelEngine`。
  - 增加 `AgentResponseJsonParser`，并补充本地模型 JSON 动作输出的单元测试。
  - 首次尝试 LiteRT-LM `0.10.2` + Kotlin `2.2.21`，可以编译并开始原生加载，但在 Redmi K60 CPU 后端没有快速到达 App 层加载完成状态。
  - 升级到 LiteRT-LM `0.12.0` + Kotlin `2.3.21`，本地构建、单元测试、APK 打包和 lint 均通过。
  - 将 App 启动行为改为只检查模型文件就绪，不在 `MainViewModel` 初始化时强制完整加载 Gemma 运行时。
  - 新增 `本地模型检查` 按钮，直接触发本地 Gemma 推理，超时时间为 180 秒，并在主界面展示结果和耗时。
  - 后续无线 ADB 曾在 streamed install 后掉线。
  - 使用 `adb connect 192.168.10.166:39791` 重新连接无线 ADB。
  - 成功安装最新 Debug APK。
  - 成功启动 `com.tencent.edgeagent/.ui.MainActivity`。
  - 验证 logcat 显示 App 使用 `Gemma LiteRT-LM engine`，并且完整运行时加载延迟到首次本地推理。
  - 用户点击 `本地模型检查` 后，抓取日志确认本地 Gemma 推理成功。
  - 本地推理输出 `NO_ACTION`，置信度 `0.95`，耗时 `18,983ms`。
  - 日志中出现 `No dispatch library found` 和 `Failed to initialize Dispatch API`，但不是致命错误；LiteRT 后续成功加载模型并完成推理。当前判断为 NPU/Dispatch 加速库缺失后回退到 CPU/GPU 路径。
  - 整理全部 Markdown 文档，将 13 个文档收敛为 6 个长期维护入口。
  - 将 `docs/ROADMAP.md` 改为 `docs/PRODUCT.md`。
  - 将开发指南、上手与测试、API 示例、AI 协作 Prompt 合并为 `docs/DEVELOPMENT.md`。
  - 将 Phase 1-5 总结合并为 `docs/HISTORY.md`，并补充 Phase 6 本地模型阶段。
  - 根据产品分层重新调整开发主线：先验收 L1 低风险任务，再推进 L2/L3 多轮任务。
  - 新增 `L1CommandRouter`，让调音量、回到桌面、打开相机、打开 Wi-Fi 设置、打开微信等低风险任务具备确定性兜底能力。
  - `AgentOrchestrator` 已调整为模型优先路由：云端可用时先进入阿里千问多轮链路；云端失败且 L1 命中时才使用确定性兜底；云端不可用时再考虑 L1 兜底或本地模型单轮链路。
  - 修复 L1 真机卡点：`ActionExecutor` 不再对所有动作强制依赖无障碍 Service。打开 App、调音量、打开 Wi-Fi/蓝牙/飞行模式设置可以使用 Application Context 执行；返回、Home、最近任务、点击、滑动、输入仍需要无障碍。
  - 修复状态机重复 Reset 误报：任务完成自动回到 IDLE 后，再收到 Reset 时只记录调试日志，不再输出非法状态转换错误。
  - 主界面快捷测试已改为 L1 验收任务：调高音量、回到桌面、打开相机、打开 WiFi 设置、打开微信。
  - 补充 `L1CommandRouterTest`，覆盖音量、相机、WiFi、Home，以及微信发消息不被 L1 接管。
  - 真机安装并执行默认任务 `打开相机` 成功：
    - 日志显示 `[AgentFlow] deterministic L1 mode action=OPEN_APP`。
    - `ActionExecutor` 执行 `OpenApp(packageName=com.android.camera)`。
    - AgentTrace 记录 `source=LOCAL_RAG`、`action=OPEN_APP`、`success=true`。
  - 随后根据产品原则再次调整：上述 L1 直接执行只保留为兜底路径，正常任务应由千问模型先做动作决策。
  - 修复模型优先链路中 `打开相机` 卡住的问题：新增 `OpenAppStrategy`，当模型在 VisionAgent 主界面误点输入框/按钮时，策略层改写为 `OPEN_APP(targetPackage)`。
  - 新增 `InferenceSource.STRATEGY`，用于区分“模型输出”和“策略改写”，避免把策略动作误记为 RAG 或云端模型。
  - 新增 `DeviceControlStrategy` 和 `SystemNavigationStrategy`，让音量、亮度、Wi-Fi/蓝牙/飞行模式、返回、Home、最近任务、关闭键盘都走模型优先 + 策略校正链路。
  - 修复输入后软键盘不关闭的问题：`ActionExecutor.executeInputText` 成功后会尝试通过无障碍返回键收起键盘。
  - 修复 `OPEN_APP` 后任务不结束的问题：`AgentExecutor` 在观察到目标包名后直接记录 `NO_ACTION` 完成状态，并在开发验收模式下尝试返回 VisionAgent 控制台。
  - 补齐相机、浏览器在 `AgentExecutor.resolveTargetPackage` 中的兜底包名，避免云端异常时无法回退。
  - 阿里千问、DeepSeek、本地 Gemma 的 Prompt 已补充 `DEVICE_CONTROL` 参数格式和输入后收键盘规则。
  - 新增 `OpenAppStrategyTest` 和 `L1StrategyTest`，覆盖打开 App、设备控制、系统导航策略改写。
  - 重新验证 `./gradlew :app:testDebugUnitTest :app:assembleDebug` 和 `./gradlew :app:lintDebug`，均通过。
  - 安装最新 Debug APK 到 Redmi K60，通过 `dev_bootstrap_permissions.sh` 开启无障碍并尝试处理屏幕录制授权；日志显示无障碍服务已重新连接。
  - 从真机日志确认 Redmi K60 的系统浏览器包名是 `com.android.browser`，已同步补充到 L1 路由、Planner、BrowserStrategy、ActionExecutor 和云端 Prompt。
  - 新增并启动 `keep_device_awake.sh`，通过 `screen_off_timeout`、`svc power stayon` 和定期 `KEYCODE_WAKEUP` 保持开发测试机亮屏。macOS 下脚本使用 `launchctl` 托管，避免普通后台进程被终端会话回收。
  - 新增 `docs/L1_TEST_MATRIX.md` 和 `l1_validation.sh`，形成 L1 真机验收闭环。
  - 补强 L1/L2 边界：`连接某个蓝牙设备`、`设置自己的壁纸`、`把时区改为阿根廷` 不再被 L1 路由或策略层接管。
  - 执行 `./l1_validation.sh`：L1 单元测试通过、Debug APK 构建通过、真机安装成功、开发权限准备完成、VisionAgent 启动成功。
  - 用户反馈 `打开相机` 能成功进入相机，但不会返回 VisionAgent。
  - 查看最近日志和最新 AgentTrace，确认该任务走的是 `l1_deterministic` 直接路径：Trace 在 `OPEN_APP/com.android.camera` 成功后立即结束，没有进入多轮 Agent 的收尾逻辑。
  - 修复 `AgentOrchestrator.executeDeterministicL1`：L1 `OPEN_APP` 成功后统一执行 `OPEN_APP/com.tencent.edgeagent` 作为测试收尾，并把收尾动作记录为 `l1_post_task_reopen_agent`。
  - 重新执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`：通过。
  - 重新安装 Debug APK 到 Redmi K60 并启动 VisionAgent；确认 App 进程和无障碍服务在线。
  - 用户复测反馈：`打开相机` 仍未成功返回 VisionAgent。
  - 再次查看最近日志和最新 AgentTrace，确认该次走的是千问模型优先多轮路径：`OPEN_APP/com.android.camera` 成功后 Trace 没有进入第 2 轮，也没有 `session_finish`。
  - 修复模型优先 `OPEN_APP` 收尾：`AgentExecutor` 在 `OPEN_APP` 执行成功后立即进入收尾，不再等待下一轮模型循环；收尾优先通过无障碍 `BACK` 回到 VisionAgent。
  - 同步调整 L1 直接路径收尾：`AgentOrchestrator` 优先用 `BACK` 返回 VisionAgent，只有返回后仍不在 VisionAgent 时才尝试重新拉起 App。
  - 重新执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`：通过。
  - 重新安装 Debug APK 到 Redmi K60 并启动 VisionAgent；确认当前前台为 `com.tencent.edgeagent/.ui.MainActivity`，无障碍服务在线。
  - 用户复测确认 `打开相机` 已成功：先进入相机，再自动返回 VisionAgent。
  - 最新 AgentTrace 显示：第 1 轮 `OPEN_APP/com.android.camera` 成功，第 2 轮 `BACK` 成功，`session_finish success=true`。L1-P0 `打开相机` 验收通过。
  - 用户反馈 `打开 WiFi 设置` 可以成功进入 Wi-Fi 设置页。
  - 查看最近 AgentTrace，确认 `DEVICE_CONTROL/WIFI_SETTINGS` 成功，当前前台为 `com.android.settings/.Settings$WifiSettingsActivity`。L1-P0 `打开 WiFi 设置` 验收通过。
  - 同时发现隐藏问题：模型优先路径会重复执行 `WIFI_SETTINGS` 直到最大轮数，然后才回落到 L1 兜底。
  - 修复 `AgentExecutor`：`TaskType.DEVICE_CONTROL` 且 `DEVICE_CONTROL` 执行成功后立即结束任务，避免音量、Wi-Fi、蓝牙、通知栏等 L1 设备控制动作重复执行。
  - 重新执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`：通过。
  - 重新安装 Debug APK 到 Redmi K60 并启动 VisionAgent；确认当前前台为 `com.tencent.edgeagent/.ui.MainActivity`，无障碍服务在线。
  - 用户反馈 `打开蓝牙设置` 成功。
  - 最新 AgentTrace 显示：规划为 `DEVICE_CONTROL`，第 1 轮 `DEVICE_CONTROL/BLUETOOTH_SETTINGS` 成功后直接 `session_finish success=true`，没有重复执行。L1-P0 `打开蓝牙设置` 验收通过。
  - 用户批量测试 L1 基础功能后反馈基本成功，但 `打开最近任务` 出现来回跳转。
  - 批量查看最近 AgentTrace：通知栏、快捷设置、声音与触感、日期时间、壁纸等设备控制入口均为第 1 轮成功并结束。
  - 定位 `打开最近任务` 的异常 Trace：模型优先路径连续 6 轮执行 `RECENTS`，在 `com.miui.home` 与 VisionAgent 间反复切换，最后超过最大轮数；后续 L1 兜底 Trace 单轮成功。
  - 修复 `AgentExecutor`：`TaskType.SYSTEM_NAVIGATION` 且 `BACK/HOME/RECENTS` 执行成功后立即结束任务，避免返回、桌面、最近任务等系统导航动作重复执行。
  - 明确当前感知机制：每轮都会采集一次 `ScreenData`；屏幕录制未授权时只采集无障碍 UI 树并使用空白 Bitmap。AgentTrace 记录包名、UI 树摘要、模型/策略响应和执行结果，不持久化完整截图图片。
  - 重新执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`：通过。
  - 重新安装 Debug APK 到 Redmi K60 并启动 VisionAgent；确认当前前台为 `com.tencent.edgeagent/.ui.MainActivity`，无障碍服务在线。
  - 新增自适应感知采集策略：UI 树每轮必采，截图只在任务/页面需要视觉理解且屏幕录制已授权时采集。
  - L1 系统导航、设备控制、打开 App 默认使用 `UI_TREE_ONLY`，避免实时截图拖慢基础能力。
  - 微信草稿、浏览器、App 内导航、通用任务、视觉关键词任务或 UI 树不可用场景使用 `UI_TREE_AND_SCREENSHOT`。
  - `ScreenData` 新增 `captureMode`，AgentTrace 和 `view_logs.sh --replay` 会显示每轮实际采集模式。
  - 重新执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`：通过。
  - 当前 ADB 未连接设备，尚未把本次 APK 安装到 Redmi K60。

## 下一步自主任务

1. 进行模型优先 L1 真机验收：系统导航、通知栏、快捷设置、音量亮度、媒体控制、常见设置入口、打开常见 App。
2. 查看模型优先 L1 真机执行后的 AgentTrace，确认每次策略改写、执行结果和失败原因都能直接从日志定位。
3. 以系统设置 App 为 L2 样板，推进时区、蓝牙、声音与触感、壁纸等多步骤任务。
4. 将本地模型健康检查结果写入 AgentTrace，形成可回放的模型运行诊断记录。
5. 为 App 增加更明确的模型状态 UI：模型文件就绪、运行时已加载、推理成功、云端兜底中、本地失败原因。
6. 保持 Qwen-VL-Max 作为复杂视觉、多轮页面理解和策略任务的云端主链路。
7. 继续强化微信草稿状态机，保证只进入草稿，不自动点击最终发送。
8. 扩展 App 专项策略库，优先覆盖浏览器、系统设置、美团、电话、微信。
9. 继续把失败日志、UI 树、截图状态、模型输出、执行结果统一沉淀到 AgentTrace。
10. 后续推进多 Agent 协作，包括规划 Agent、视觉/感知 Agent、执行 Agent、反思 Agent、安全 Agent。

## 权限与测试边界

- 产品能力必须由 Android App 内的 Agent 执行，不能依赖 ADB 代替用户点击、输入或完成业务流程。
- ADB 只允许作为开发测试脚手架使用：安装 APK、启动 App、准备权限、抓日志、拉取 Trace。
- `keep_device_awake.sh` 属于开发测试脚手架，只能用于防止测试机熄屏，不能用于替 Agent 完成业务动作。
- 无障碍权限属于系统敏感授权，正式产品必须由用户主动开启；测试机上可以用 `dev_bootstrap_permissions.sh` 尝试通过 `settings put secure` 开启。
- 屏幕录制权限基于 `MediaProjection`，普通 App 不能真正静默授权；测试脚本只能自动点击系统弹窗。如果 MIUI 拦截，仍可能需要一次人工确认。
- 当前 Redmi K60 如果处于锁屏/通知遮罩，ADB 可以唤醒但不能可靠绕过安全锁屏；执行 UI 触发型 Agent 测试前，需要保证测试机已解锁。
