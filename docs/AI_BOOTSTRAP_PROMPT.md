# AI 协作 Prompt

将下面内容提供给新的 AI 编程助手，可以让它快速理解本项目并继续开发。

```text
你正在开发 VisionAgent Android。

项目目标：
构建一个真实 Android Agent。它通过无障碍服务、屏幕截图、结构化 UI 树、视觉模型、本地 RAG 和多 Agent 协作，在用户授权范围内完成可验证手机操作。

当前原则：
1. 每轮只执行一个最小动作。
2. 复杂 App 任务必须使用策略约束。
3. 禁止自动执行发送、支付、下单、删除、转账、提交等高风险最终动作。
4. 微信任务只允许填草稿，不允许自动点击发送。
5. 截图和 UI 树都不可用时，不能猜测页面内容。
6. 新增策略必须可测试、可解释、可回放。

核心模块：
- AgentOrchestrator：UI 到 Agent 的统一入口。
- AgentExecutor：多轮观察、决策、执行循环。
- PlannerAgent：任务分类、目标包名、安全模式、RAG 检索。
- ReflectionAgent：失败、重复、等待、不可观测状态分析。
- ActionGuard：高风险动作拦截。
- LocalRagEngine：本地策略检索。
- UITreeExtractor：文本 UI 树和结构化 UiNode。
- ScreenCaptureService：MediaProjection 截图帧缓存。
- ActionExecutor：无障碍动作执行。
- CloudFallbackManager：云端视觉模型调用。

当前默认云端模型：
阿里云百炼 qwen-vl-max。

当前优先开发任务：
1. AgentTrace 失败日志。
2. 高风险动作确认 UI。
3. WechatStrategy 草稿状态机。
4. RAG 持久化和向量检索。
5. 真机评测任务集。

开发要求：
- 不要把业务流程写进 Activity。
- 不要绕过 ActionGuard。
- 不要提交 local.properties 或 API Key。
- 每次修改后运行：
  ./gradlew :app:assembleDebug :app:testDebugUnitTest

输出代码时遵循现有 Kotlin 风格，保持改动聚焦。
```
