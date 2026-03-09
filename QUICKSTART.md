# 🚀 快速开始指南

## 📋 前置要求

- Android Studio（最新版本）
- Android 设备或模拟器（API 24+）
- DeepSeek API Key（可选，用于云端功能）

---

## ⚡ 5 分钟快速启动

### 1. 克隆项目（如果还没有）

```bash
git clone <your-repo-url>
cd AI-Agent-Vision-Android
```

### 2. 配置云端 API（可选）

如果你想使用云端 AI 功能：

1. 访问 https://platform.deepseek.com/ 注册账号
2. 获取 API Key
3. 打开 `app/src/main/java/com/tencent/edgeagent/data/cloud/CloudConfig.kt`
4. 替换 API Key：

```kotlin
const val DEEPSEEK_API_KEY = "sk-your-actual-api-key-here"
```

**如果不想使用云端功能**，设置：
```kotlin
const val ENABLE_CLOUD = false  // 纯本地模式
```

### 3. 构建并安装

```bash
./gradlew installDebug
```

或在 Android Studio 中点击 Run 按钮。

### 4. 开启无障碍权限

**这是最重要的一步！**

1. 打开设备的"设置"
2. 进入"无障碍"（Accessibility）
3. 找到"EdgeAgent"
4. 开启服务

**路径示例**：
- 小米：设置 → 更多设置 → 无障碍 → EdgeAgent
- 华为：设置 → 辅助功能 → 无障碍 → EdgeAgent
- 原生 Android：设置 → 无障碍 → EdgeAgent

### 5. 测试功能

打开 EdgeAgent 应用，点击测试按钮：

- **点击屏幕中心** - 测试点击功能
- **向上滑动** - 测试滑动功能
- **打开 Chrome** - 测试应用操作
- **打开 YouTube** - 测试云端推理

观察日志区域，查看执行过程。

---

## 🎯 功能测试清单

### ✅ 基础功能（无需云端）

- [ ] 点击屏幕中心 - 应该看到屏幕被点击
- [ ] 向上滑动 - 应该看到屏幕滑动
- [ ] 返回操作 - 应该执行返回
- [ ] 状态机正常工作 - 状态显示正确变化

### ✅ 云端功能（需要配置 API Key）

- [ ] 云端状态显示"已启用: DeepSeek"
- [ ] 低置信度自动调用云端
- [ ] 云端推理成功返回结果
- [ ] 复杂任务使用云端处理

---

## 📱 查看日志

### 方法 1：应用内日志

应用界面底部有日志区域，实时显示执行过程。

### 方法 2：Logcat

```bash
adb logcat | grep EdgeAgent
```

关键日志：
```
EdgeAgent: 模型预热完成
EdgeAgent: 云端服务初始化成功
EdgeAgent: 开始测试推理: 点击屏幕中心
EdgeAgent: 本地推理完成: confidence=0.85
EdgeAgent: 执行成功: 点击成功
```

---

## ⚠️ 常见问题

### Q1: 点击测试按钮没反应？

**A**: 检查无障碍权限是否开启：
1. 设置 → 无障碍 → EdgeAgent
2. 确保开关是打开状态
3. 如果已开启，尝试关闭再重新开启

### Q2: 显示"无障碍服务未启动"？

**A**: 
1. 确认已开启无障碍权限
2. 重启应用
3. 查看 Logcat 是否有错误信息

### Q3: 云端状态显示"未配置 API Key"？

**A**: 
1. 打开 `CloudConfig.kt`
2. 确认 `DEEPSEEK_API_KEY` 不是 "YOUR_DEEPSEEK_API_KEY_HERE"
3. 确认 `ENABLE_CLOUD = true`
4. 重新构建并安装应用

### Q4: 云端调用失败？

**A**: 
1. 检查设备是否联网
2. 检查 API Key 是否正确
3. 查看 Logcat 错误信息
4. 如果是网络问题，会自动降级到本地推理

### Q5: 点击/滑动没有实际效果？

**A**: 
1. 确认无障碍权限已开启
2. 某些系统界面（如设置）可能无法操作
3. 尝试在其他应用中测试
4. 查看日志是否显示"执行成功"

---

## 🔧 开发模式

### 纯本地模式（无需网络）

适合开发和测试基础功能：

```kotlin
// CloudConfig.kt
const val ENABLE_CLOUD = false
```

### 云端模式（需要网络）

适合测试完整的 AI 功能：

```kotlin
// CloudConfig.kt
const val ENABLE_CLOUD = true
const val DEEPSEEK_API_KEY = "sk-your-key"
```

---

## 📚 下一步

### 学习项目架构

阅读文档：
1. `ARCHITECTURE.md` - 了解整体架构
2. `PHASE1_SUMMARY.md` - 状态机和意图路由
3. `PHASE2_SUMMARY.md` - 无障碍服务
4. `PHASE3_SUMMARY.md` - 动作执行
5. `PHASE4_SUMMARY.md` - 云端 API

### 扩展功能

参考 `PHASE4_SUMMARY.md` 的"下一步"部分：
- 实现输入文本功能
- 实现打开应用功能
- 实现设备控制功能
- 集成本地 RAG
- 集成本地多模态模型

### 准备面试

阅读各 Phase 文档的"面试亮点"部分，准备技术问题回答。

---

## 💡 提示

### 性能优化

- Bitmap 对象池已实现，避免频繁 GC
- 云端图片自动压缩到 1024px
- 30 秒超时保护

### 隐私保护

- 设备控制和文本输入不上云
- 可完全禁用云端服务
- 所有数据优先本地处理

### 成本控制

- 只有低置信度或复杂任务才调用云端
- 图片压缩减少传输成本
- DeepSeek API 性价比高

---

## 🎉 成功标志

如果你看到以下内容，说明项目运行成功：

✅ 应用正常启动
✅ 模型信息显示"MockVLM"
✅ 云端状态显示"已启用: DeepSeek"（如果配置了 API Key）
✅ 点击测试按钮后，日志显示完整的执行过程
✅ 屏幕真的被点击或滑动了

---

**祝你使用愉快！如有问题，请查看各 Phase 文档或提交 Issue。**
