# Phase 2 历史记录：无障碍感知与执行

本文档记录无障碍服务、屏幕感知和手势执行的早期建设。

## 目标

让 Agent 具备 Android 手机上的“眼”和“手”：

- 读取当前窗口 UI 树。
- 捕获屏幕截图。
- 执行点击、滑动、返回、Home 等动作。

## 完成内容

| 内容 | 状态 |
| --- | --- |
| `EdgeAgentAccessibilityService` | 完成 |
| `GestureExecutor` | 完成 |
| `ScreenCaptureManager` | 完成 |
| `UITreeExtractor` | 完成 |
| 无障碍服务配置 | 完成 |

## 当前演进

Phase 2 之后已增强：

- `ScreenCaptureService` 改为持续帧缓存。
- `UITreeExtractor` 增加结构化 `UiNode`。
- `ActionExecutor` 增加坐标越界校验。
- 文本输入增加 `ACTION_SET_TEXT` 和剪贴板降级。

## 已知限制

- 不同厂商 ROM 对无障碍权限限制不同。
- WebView、自绘 UI、复杂 App 页面可能导致 UI 树信息不足。
- 屏幕录制权限需要用户授权。

## 验收建议

真机验证顺序：

1. 开启无障碍服务。
2. 授权屏幕录制。
3. 执行点击。
4. 执行滑动。
5. 执行返回和 Home。
6. 捕获 UI 树和截图。
