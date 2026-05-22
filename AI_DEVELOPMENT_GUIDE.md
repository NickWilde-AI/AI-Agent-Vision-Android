# VisionAgent Android 开发指南

本文档面向参与项目开发的人和 AI 编程助手，说明工程边界、代码规范、调试方法和当前优先级。

## 项目定位

VisionAgent Android 是一个真实 Android Agent 工程。它使用无障碍服务和屏幕感知能力理解手机当前状态，通过多 Agent 决策和安全策略执行可验证动作。

项目目标：

- 构建安全可控的手机 Agent 主链路。
- 用 RAG 和 App 策略约束模型行为。
- 将复杂任务拆成单步执行和逐轮验证。
- 在高风险动作前停止并请求用户确认。

项目不追求：

- 让模型无约束自由点击。
- 绕过第三方 App 风控。
- 自动发送、支付、下单等不可逆动作。

## 当前关键模块

| 模块 | 路径 | 职责 |
| --- | --- | --- |
| UI | `app/src/main/java/com/tencent/edgeagent/ui` | 命令输入、权限状态、执行进度展示 |
| 编排 | `domain/agent/AgentOrchestrator.kt` | 接收 UI 命令，选择本地或多轮 Agent 流程 |
| 多轮执行 | `domain/agent/AgentExecutor.kt` | 屏幕观察、模型决策、动作执行、反馈循环 |
| Planner | `domain/agent/multi/PlannerAgent.kt` | 任务分类、目标包名、安全模式、RAG 检索 |
| Reflection | `domain/agent/multi/ReflectionAgent.kt` | 分析失败、重复动作、不可观测状态 |
| Safety | `domain/agent/safety/ActionGuard.kt` | 拦截高风险最终动作 |
| RAG | `data/rag/LocalRagEngine.kt` | 本地策略检索 |
| 感知 | `data/perception` | 截图、结构化 UI 树 |
| 执行 | `data/execution` | 点击、滑动、输入、打开 App、系统控制 |
| 云端 | `data/cloud` | 阿里云百炼/千问、DeepSeek 等模型客户端 |
| 服务 | `service` | AccessibilityService 和 MediaProjection 服务 |

## 开发原则

1. 单轮只执行一个最小动作。
2. 执行前必须经过安全检查。
3. Prompt 必须包含任务边界和安全约束。
4. UI 树和截图都不可靠时，禁止猜测页面内容。
5. App 专项流程必须状态机化。
6. 高风险动作必须等待用户确认。
7. 新增策略必须有测试或可验证日志。

## 常用命令

构建：

```bash
./gradlew :app:assembleDebug
```

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

完整本地验证：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

查看设备：

```bash
adb devices
```

安装：

```bash
./gradlew :app:installDebug
```

日志：

```bash
adb logcat | grep -E "AgentTask|PlannerAgent|ReflectionAgent|ActionGuard|ActionExecutor|ScreenCapture"
```

## API Key 管理

只允许写入 `local.properties`：

```properties
ALIYUN_API_KEY=your-api-key
```

不要提交：

- `local.properties`
- 任何真实 API Key
- 运行日志
- 截图样本，除非脱敏且明确用于测试

## 推荐开发任务

当前最高优先级：

1. `AgentTrace` 失败日志。
2. 高风险动作确认 UI。
3. `WechatStrategy` 草稿状态机。
4. RAG 持久化。
5. 真机评测任务集。

完整产品规划见 [docs/ROADMAP.md](docs/ROADMAP.md)。

## 代码规范

- 新增业务逻辑优先放在 Domain 层。
- Android API 封装放在 Data 或 Service 层。
- ViewModel 不直接编排 Agent 决策。
- 不在 Activity 中写业务流程。
- 不在 prompt 中写不可执行的泛化愿望，必须写具体约束。
- 新增高风险动作必须同步更新 `ActionGuard`。

## 测试要求

新增以下内容必须补测试：

- RAG 策略匹配。
- 安全拦截规则。
- 任务分类规则。
- 文本解析或模型输出解析。
- 坐标校验逻辑。

当前已有测试：

- RAG 命中微信草稿策略。
- RAG 命中高风险确认策略。
- ActionGuard 拦截微信发送点击。

## 真机验收清单

每次改动主链路后至少验证：

- App 能启动。
- 无障碍服务能开启。
- 屏幕录制能授权。
- 点击中心、滑动、返回、Home 正常。
- 打开设置正常。
- 云端状态显示阿里云百炼。
- 微信任务不会自动点击发送。
- 高风险动作会停止。

## Git 工作流

提交前：

```bash
git status -sb
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

提交信息使用简洁中文，例如：

```bash
git commit -m "完善多 Agent 安全策略"
```

推送：

```bash
git push origin main
```

## 文档维护规则

- README 只写项目现状和入口。
- ROADMAP 写产品定位、用户场景、版本规划和上线边界。
- ARCHITECTURE 写真实架构，不写愿景口号。
- GETTING_STARTED 写运行步骤。
- PHASE 文档只作为历史记录。
