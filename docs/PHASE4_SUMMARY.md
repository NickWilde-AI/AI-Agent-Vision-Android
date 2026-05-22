# Phase 4 历史记录：云端模型接入

本文档记录云端模型客户端和统一推理接口的建设。

## 目标

通过统一接口接入云端模型，让 Agent 能基于截图和 UI 树输出结构化动作。

## 完成内容

| 内容 | 状态 |
| --- | --- |
| `ICloudClient` | 完成 |
| `CloudFallbackManager` | 完成 |
| `AliyunClient` | 完成 |
| `DeepSeekClient` | 保留 |
| `CloudConfig` | 完成 |
| JSON 动作解析 | 完成 |
| API Key 本地注入 | 完成 |

## 当前默认模型

当前默认 Provider：

```kotlin
CloudProvider.ALIYUN
```

当前默认模型：

```text
qwen-vl-max
```

API Key 来源：

```properties
ALIYUN_API_KEY=your-api-key
```

## 设计约束

模型必须返回单个 JSON 动作：

```json
{
  "action": "CLICK",
  "params": {
    "x": 540,
    "y": 1200,
    "description": "点击搜索框"
  },
  "confidence": 0.95,
  "reasoning": "根据 UI 树坐标"
}
```

模型不应输出：

- Markdown。
- 多个动作。
- 自然语言解释作为最终结果。
- 不在屏幕范围内的坐标。

## 当前增强

Phase 4 之后已增强：

- 截图不可用时，阿里云客户端不再发送空白图。
- 输入坐标缺失时不再默认点击 `(0,0)`。
- Prompt 注入 RAG、规划和反思信息。
- 高风险动作由 `ActionGuard` 拦截。

## 已知限制

- DeepSeek 客户端主要作为文本模型保留，不适合作为主视觉模型。
- 云端响应仍可能格式错误，需要继续增强解析和重试。
- 复杂 App 成功率不能只靠模型能力，必须依赖 App 策略。
