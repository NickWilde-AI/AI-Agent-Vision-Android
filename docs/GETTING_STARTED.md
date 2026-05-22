# 上手与测试

本文档说明如何在本地构建、运行和验证 VisionAgent Android。

## 环境要求

- Android Studio
- JDK 17
- Android SDK
- Android 7.0+ 设备或模拟器
- 推荐真机测试，因为无障碍和屏幕录制在模拟器上的行为不完全可靠

## 克隆项目

```bash
git clone https://github.com/NickWilde-AI/AI-Agent-Vision-Android.git
cd AI-Agent-Vision-Android
```

## 配置 API Key

编辑项目根目录 `local.properties`：

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
ALIYUN_API_KEY=your-api-key
```

注意：

- 不要把 API Key 写入源码。
- 不要提交 `local.properties`。
- 当前默认 Provider 是 `CloudProvider.ALIYUN`。
- 当前默认视觉模型是 `qwen-vl-max`。

## 构建

```bash
./gradlew :app:assembleDebug
```

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

完整本地验证：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## 安装

连接设备后：

```bash
adb devices
./gradlew :app:installDebug
```

如果没有设备，`adb devices` 会显示空列表，此时无法做端到端验证。

## 必要权限

首次运行后需要手动开启：

1. 无障碍服务
   - 设置 -> 无障碍 -> VisionAgent -> 开启

2. 屏幕录制
   - App 内点击屏幕录制授权

3. 修改系统设置
   - 亮度调节场景需要
   - App 会跳转到授权页

## 推荐验证顺序

先验证低风险能力：

1. 打开 App。
2. 点击屏幕中心。
3. 向上滑动。
4. 返回。
5. Home。
6. 打开设置。
7. 调节音量。
8. 浏览器搜索。

再验证受控 App 能力：

1. 打开微信。
2. 搜索联系人。
3. 进入聊天页。
4. 输入草稿。
5. 确认不会自动点击发送。

不要用自动发送消息作为第一条验收任务。

## 日志

过滤核心日志：

```bash
adb logcat | grep -E "AgentTask|PlannerAgent|ReflectionAgent|ActionGuard|ActionExecutor|ScreenCapture"
```

重点关注：

- `PlannerAgent` 是否识别正确任务类型。
- `LocalRagEngine` 是否命中策略。
- `ReflectionAgent` 是否识别失败和重复动作。
- `ActionGuard` 是否拦截高风险动作。
- `ScreenCaptureService` 是否返回真实截图。

## 常见问题

### 无障碍服务不可用

检查：

- 是否在系统设置中开启服务。
- App 是否被系统后台限制。
- 当前设备 ROM 是否限制无障碍服务。

### 截图为空

检查：

- 是否已授权屏幕录制。
- `ScreenCaptureService` 是否在运行。
- 是否刚刚切换页面，等待一轮后再观察。

### 千问接口失败

检查：

- `local.properties` 是否有 `ALIYUN_API_KEY`。
- API Key 是否有效。
- 设备网络是否可用。
- 阿里云百炼账号是否已开通对应模型。

### 微信任务失败

这是预期中的高难场景。当前策略目标是填草稿，不自动发送。

优先排查：

- 是否进入微信。
- UI 树是否能看到搜索框或输入框。
- 是否命中 `wechat.draft_only` RAG 策略。
- 是否被 `ActionGuard` 拦截发送按钮。

## 验收记录模板

每次真机验证建议记录：

```text
设备：
Android 版本：
ROM：
App 版本：
任务：
是否真实截图：
UI 树是否可用：
模型返回动作：
执行结果：
失败原因：
```

失败样本后续应进入 `AgentTrace` 和 RAG 记忆。
