# Phase 3 历史记录：动作执行器

本文档记录 `AgentResponse` 到真实 Android 动作的执行层建设。

## 目标

把模型或策略输出的动作转换为真实无障碍操作。

## 完成内容

| 动作 | 状态 |
| --- | --- |
| CLICK | 完成 |
| LONG_CLICK | 完成 |
| SWIPE | 完成 |
| INPUT_TEXT | 完成 |
| BACK | 完成 |
| HOME | 完成 |
| RECENTS | 完成 |
| OPEN_APP | 完成 |
| DEVICE_CONTROL | 基础完成 |
| WAIT | 完成 |
| NO_ACTION | 完成 |

## 当前增强

Phase 3 之后已增强：

- 坐标越界校验。
- 打开 App 优先使用系统启动 Intent，并保留桌面图标兜底。
- 文本输入优先 `ACTION_SET_TEXT`，失败后剪贴板粘贴。
- 亮度调节会检查 `WRITE_SETTINGS` 授权。
- 高风险动作交给 `ActionGuard` 拦截。

## 安全边界

执行层只负责执行动作，不负责决定动作是否安全。安全判断必须在 `ActionGuard` 中完成。

## 验收建议

优先验证：

1. 坐标点击。
2. 滑动。
3. 文本输入。
4. 打开设置。
5. 返回和 Home。
6. 越界坐标不会执行。
