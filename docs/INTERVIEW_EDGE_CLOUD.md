# 端云协同岗位面试文档

本文档用于准备 Android 端侧模型、车机 AI、端云协同 Agent 相关岗位面试。目标不是堆概念，而是把 VisionAgent Android 项目包装成一个可以讲清楚架构、工程实现、模型部署、安全边界和后续演进的作品。

## 岗位定位

目标岗位可以概括为：

```text
Android / 车机系统工程
+ 端侧模型部署
+ 云端大模型协同
+ 多模态 Agent
+ 安全可控执行
```

面试官通常不只考“会不会调 API”，而是会看候选人是否理解：

- Android 系统能力和权限边界。
- 端侧模型部署、量化、推理性能和资源约束。
- 云端大模型和本地小模型如何协同。
- Agent 如何感知、规划、执行、验证和回放。
- 车载场景下哪些能力可以自动化，哪些必须安全拦截。

## 一句话项目介绍

VisionAgent Android 是一个 Android 端云协同 Agent 原型系统。它在手机端实现屏幕感知、UI 树提取、无障碍执行、Trace 回放、RAG 策略记忆和本地模型健康检查，同时通过 Qwen-VL-Max 承担复杂视觉理解和多轮决策。

面试版本可以这样说：

```text
我做了一个 Android 端云协同 Agent 原型：端侧负责感知、执行、安全校验和日志回放；云端视觉大模型负责复杂页面理解；本地模型用于离线兜底和端侧推理验证。这个架构可以迁移到车机 AAOS 场景，用 Car API / VHAL 替代普通 App 操作。
```

## 核心能力栈

### Android 基础

必须能讲清楚：

- Kotlin / Android 原生开发。
- Service、前台服务、生命周期。
- AccessibilityService。
- MediaProjection 屏幕录制。
- Intent、包名、Activity 启动。
- 权限申请、后台限制、系统弹窗。
- 本地文件、日志、持久化。
- Gradle、APK 构建、真机调试、ADB。

项目对应：

- `EdgeAgentAccessibilityService`
- `ScreenCaptureService`
- `ActionExecutor`
- `AgentTraceStore`
- `deploy_device.sh`
- `view_logs.sh`

### 端侧模型部署

必须能讲清楚：

- LiteRT / TensorFlow Lite。
- llama.cpp / GGUF。
- Cactus / MNN / NCNN / ONNX Runtime。
- 4bit / 8bit 量化。
- CPU / GPU / NPU / DSP 推理差异。
- 首 token 延迟、吞吐、内存、模型加载耗时。
- KV Cache、上下文长度、流式输出。
- 模型版本管理、灰度、回滚。

项目当前状态：

- 已接入 Gemma 4 E2B + LiteRT-LM。
- 已完成 Redmi K60 真机本地模型健康检查。
- 当前本地模型作为端侧基座和兜底储备，不作为复杂视觉 Agent 主力。

### 云端大模型

必须能讲清楚：

- API Key 管理。
- Qwen-VL-Max 多模态输入。
- 图片压缩和上传。
- Prompt 约束。
- JSON 动作协议。
- 超时、重试、降级。
- 云端失败后的本地兜底。

项目对应：

- `AliyunClient`
- `CloudFallbackManager`
- `CloudConfig`
- `AgentResponseJsonParser`

### Agent 架构

必须能讲清楚：

- 感知：截图、UI 树、当前包名、屏幕状态。
- 规划：任务分类、动作选择、低风险兜底。
- 执行：点击、滑动、输入、返回、打开 App、设备控制。
- 反思：判断是否成功、是否需要下一步。
- 安全：高风险拦截、草稿模式、用户确认。
- 回放：失败日志、动作链路、模型输出。

项目对应：

- `AgentOrchestrator`
- `AgentExecutor`
- `PlannerAgent`
- `ReflectionAgent`
- `ActionGuard`
- `L1CommandRouter`
- `AgentTraceStore`

## 车端能力栈

如果面试方向偏车机，需要额外准备：

### AAOS 和车机系统

关键词：

- Android Automotive OS。
- Android Auto 和 Android Automotive 的区别。
- Car API。
- Car Service。
- CarPropertyManager。
- Vehicle HAL / VHAL。
- AIDL HAL。
- SELinux。
- 车机多用户、多屏、驾驶状态。

核心理解：

```text
手机 Agent 可以模拟点击，但车端核心控制不应该依赖点击 UI。
车端应该通过 Car API、系统服务或 VHAL 读取和控制车辆能力。
```

### 车端 Agent 场景

典型任务：

- 语音导航到某地。
- 查询车辆说明书。
- 解释故障灯。
- 查询胎压、电量、油量、续航。
- 调节空调、座椅、车窗、氛围灯。
- 播放音乐、电台、播客。
- 查询附近充电桩、停车场。
- 根据驾驶状态限制高风险交互。

车端不能直接照搬手机 Agent。车端更看重：

- 安全优先。
- 驾驶中少打扰。
- 可解释。
- 可审计。
- 弱网可用。
- 隐私保护。

## 端云协同架构

推荐面试架构：

```text
用户输入 / 语音 / 屏幕 / 车辆信号
-> 本地感知层
-> 本地任务路由器
-> 简单任务本地执行
-> 复杂任务调用云端大模型
-> 云端返回计划或动作
-> 本地安全策略校验
-> 调用 Android 能力 / Car API / VHAL
-> 执行结果验证
-> Trace 日志和失败回放
```

设计原则：

- 本地优先处理低风险、低延迟、隐私敏感任务。
- 云端处理复杂视觉、多轮推理、大上下文和开放问题。
- 本地安全策略拥有最终执行裁决权。
- 模型输出必须结构化，不允许自由文本直接驱动执行。
- 高风险动作必须二次确认或只进入草稿态。
- 所有执行链路必须可回放、可审计、可定位。

## Qwen 和 GUI-Owl 怎么选

### 手机 Agent

优先评估 GUI-Owl。

原因：

- GUI-Owl 更贴近 GUI grounding。
- 更适合看屏幕、定位控件、输出点击坐标。
- 更适合手机、电脑、浏览器自动化。

### 车端 Agent

优先 Qwen。

原因：

- 车端核心不是点屏幕，而是理解用户意图、车辆状态和安全规则。
- Qwen-VL / Qwen3-VL 更适合通用多模态、RAG、语音助手和复杂推理。
- 车控应通过 Car API / VHAL，而不是 GUI 点击。

### 当前项目路线

```text
Qwen-VL-Max：云端主链路
Gemma LiteRT-LM：已跑通的本地基座
GUI-Owl Q4：手机 GUI Agent 本地实验候选
Qwen2.5-VL/Qwen3-VL Q4：车端/通用多模态本地实验候选
```

## RAG 在车端的作用

车端 RAG 不是图书管理系统，而是车辆知识和策略检索系统。

适用数据：

- 车辆说明书。
- 故障码解释。
- 保养手册。
- 车机功能文档。
- App 操作策略。
- 用户偏好。
- 历史任务记录。
- 常见失败案例。

典型问题：

```text
这个故障灯是什么意思？
冬天除雾怎么开？
为什么我的车不能快充？
这个设置在哪里？
上次我喜欢的空调温度是多少？
```

回答策略：

- 本地 RAG 先查车辆手册和用户偏好。
- 不确定时调用云端模型综合解释。
- 涉及车控时返回建议或需要用户确认的动作。

## 安全边界

面试中必须强调：

```text
大模型不能直接控制安全关键功能。
模型只负责理解、规划和建议，真正执行前必须经过本地规则、安全策略和用户确认。
```

需要拦截或确认的操作：

- 支付、下单、转账。
- 发送消息、发布内容。
- 删除数据。
- 车辆动力、驾驶相关控制。
- 影响驾驶安全的设置。
- 不可逆系统确认。

可自动执行的低风险任务：

- 打开 App。
- 打开设置页面。
- 调节媒体音量。
- 查询信息。
- 生成草稿。
- 打开导航但不自动确认高风险选择。

## 面试讲法

### 介绍项目

```text
我这个项目不是单纯 App 自动化，而是端云协同 Agent 原型。Android 端负责感知、执行、安全和日志；云端 Qwen-VL-Max 负责复杂视觉理解；本地 Gemma LiteRT-LM 验证端侧模型链路。后续可以替换为 GUI-Owl 或 Qwen2.5-VL 的 4bit 本地量化版本。
```

### 讲端云协同

```text
我不会把所有任务都丢给云端，也不会强行纯本地。我的设计是本地先做任务分类和安全判断，低风险任务本地执行，复杂页面理解调用云端 VLM，云端结果回到本地后还要经过 ActionGuard 校验，最后执行并写入 Trace。
```

### 讲车端迁移

```text
迁移到车端后，屏幕点击不是核心能力。手机上的 ActionExecutor 可以替换为 Car API / VHAL 调用层，RAG 数据换成车辆手册、故障码和用户偏好，本地安全策略根据驾驶状态限制操作。
```

### 讲本地模型

```text
我已经在 Android 真机上跑通 Gemma 4 E2B + LiteRT-LM，后续会新增 GGUFLocalModelEngine，对比 GUI-Owl 和 Qwen2.5-VL 的 4bit/8bit 量化版本，重点看首 token 延迟、内存占用、坐标输出稳定性和多轮任务成功率。
```

## 简历写法

可以写成：

```text
VisionAgent Android：端云协同 Android Agent 原型
- 基于 AccessibilityService、MediaProjection 和 UI Tree 实现 Android 屏幕感知与动作执行。
- 接入 Qwen-VL-Max 云端多模态模型，实现复杂页面理解和结构化动作决策。
- 接入 Gemma 4 E2B + LiteRT-LM，完成 Android 真机端侧模型健康检查。
- 设计 Planner / Reflection / ActionGuard 多 Agent 链路，实现任务规划、执行校验和高风险动作拦截。
- 实现 AgentTrace 日志和失败回放，支持真机问题定位。
- 建立本地 RAG 策略记忆和 App 专项策略库，为车端手册 RAG、故障码问答和端侧偏好记忆提供迁移基础。
```

## 近期补强任务

优先级从高到低：

1. 完成 L1 基础能力稳定验收。
2. 将本地模型健康检查写入 AgentTrace。
3. 新增 `GGUFLocalModelEngine` 实验接口。
4. 下载并评估 GUI-Owl-1.5-2B Q4。
5. 下载并评估 Qwen2.5-VL-3B Q4。
6. 增加车端 RAG 示例数据：车辆手册、故障灯、空调设置、导航场景。
7. 写一份 AAOS / VHAL 迁移设计文档。
8. 增加端云协同路由策略：本地、云端、兜底、人工确认。

## 面试复习清单

必须能回答：

- 什么是端云协同？
- 为什么不能纯云端？
- 为什么不能纯本地？
- 4bit 和 8bit 的区别是什么？
- 本地模型慢怎么办？
- 云端失败怎么办？
- 模型输出乱点怎么办？
- 车端为什么不能靠模拟点击？
- RAG 在车机里有什么用？
- 大模型如何安全控制车辆功能？
- 如何做 Trace、回放和审计？
- 如何评估本地模型是否能上线？

## 当前结论

对面试最有价值的路线是：

```text
继续用 Qwen-VL-Max 打通产品能力
+ 保留 Gemma LiteRT-LM 作为已跑通端侧证明
+ 新增 GUI-Owl / Qwen2.5-VL 本地量化实验
+ 把项目包装成可迁移到 AAOS / 车端的端云协同 Agent 架构
```

这条路线既能体现 Android 工程能力，也能体现端侧模型、云端模型、RAG、多 Agent、安全策略和车端迁移思维。
