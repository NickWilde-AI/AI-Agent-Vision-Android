# Phase 5 历史记录：多轮 Agent 闭环

本文档记录从单轮推理升级到多轮观察、决策、执行、验证闭环的过程。

## 目标

让 Agent 不再一次性规划完整任务，而是采用逐轮反馈：

```text
观察屏幕 -> 决策一个动作 -> 执行动作 -> 等待页面变化 -> 再次观察
```

## 完成内容

| 内容 | 状态 |
| --- | --- |
| `AgentExecutor` 多轮循环 | 完成 |
| 屏幕捕获验证 | 完成 |
| UI 树 prompt 注入 | 完成 |
| 打开 App 确定性首步 | 完成 |
| 重复动作检测 | 完成 |
| WAIT 循环检测 | 完成 |
| 每轮决策回调 | 完成 |

## 当前增强

Phase 5 之后已接入：

- `PlannerAgent`
- `LocalRagEngine`
- `ReflectionAgent`
- `ActionGuard`

当前主循环已经不是纯 LLM 自由决策，而是：

```text
PlannerAgent
  -> RAG
  -> ReflectionAgent
  -> Cloud Model
  -> ActionGuard
  -> ActionExecutor
```

## 安全策略

微信任务默认只填草稿：

```text
打开微信 -> 搜索联系人 -> 进入聊天 -> 输入草稿 -> NO_ACTION
```

禁止自动点击：

- 发送
- 支付
- 下单
- 删除
- 转账
- 提交

## 已知限制

- App 专项策略还没有完全状态机化。
- 没有失败轨迹持久化。
- 还缺少用户确认 UI。
- 真机端到端验证仍是关键工作。

## 下一步

1. 增加 `AgentTrace`。
2. 增加确认弹窗。
3. 实现 `WechatStrategy`。
4. 将失败样本写入 RAG。
