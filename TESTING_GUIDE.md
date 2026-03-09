# EdgeAgent Android 测试指南

## 📱 安装与运行

### 1. 构建 APK

```bash
cd /Users/chenpeng/Documents/Tencent/TencentCodeing/AI-Agent-Vision-Android
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

### 2. 安装到设备

```bash
# 方式 1：使用 Gradle
./gradlew installDebug

# 方式 2：使用 adb
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. 启动应用

在设备上找到 "EdgeAgent" 应用并打开。

## 🧪 功能测试

### 测试 1：基础推理流程

**步骤**：
1. 打开应用
2. 等待模型信息加载（应显示 "MockVLM 1.0.0-mock"）
3. 点击 "测试推理：点击屏幕中心" 按钮
4. 观察状态变化：
   ```
   IDLE → PERCEIVING → REASONING_LOCAL → EXECUTING → COMPLETED → IDLE
   ```
5. 查看 "最后响应" 卡片：
   - 来源：MOCK
   - 动作：CLICK
   - 置信度：0.60-0.95（随机）
   - 推理时间：1200-1800ms

**预期结果**：
- 状态快速切换
- 推理时间约 1.5 秒
- 显示点击坐标（屏幕中心）

### 测试 2：滑动操作

**步骤**：
1. 点击 "测试推理：向上滑动" 按钮
2. 观察响应信息

**预期结果**：
- 动作：SWIPE
- 显示滑动起点和终点坐标

### 测试 3：应用操作

**步骤**：
1. 点击 "测试推理：打开微信" 按钮
2. 观察响应信息

**预期结果**：
- 动作：OPEN_APP
- 包名：com.example.微信

### 测试 4：云端兜底逻辑

**说明**：Mock 引擎有 30% 概率生成低置信度（< 0.75）

**步骤**：
1. 多次点击测试按钮（建议 10 次以上）
2. 观察 Logcat 日志

**预期结果**：
- 约 30% 的请求会触发 "需要云端兜底" 日志
- 状态转换：`REASONING_LOCAL → REASONING_CLOUD → EXECUTING`

## 📊 Logcat 日志查看

### 过滤 EdgeAgent 日志

```bash
adb logcat -s EdgeAgent
```

或在 Android Studio 中：
1. 打开 Logcat 窗口
2. 过滤器输入：`tag:EdgeAgent`

### 关键日志示例

```
D/MainViewModel: MainViewModel 初始化
D/MockModelEngine: MockModelEngine 预热中...
D/MockModelEngine: MockModelEngine 预热完成
D/MainViewModel: 模型预热完成: ModelInfo(name=MockVLM, version=1.0.0-mock, ...)

D/MainViewModel: 开始测试推理: 点击屏幕中心
D/AgentStateMachine: 状态转换: IDLE → PERCEIVING
D/IntentRouter: 意图识别: 点击屏幕中心 → APP_OPERATION (云端兜底: true)
D/AgentStateMachine: 状态转换: PERCEIVING → REASONING_LOCAL
D/MockModelEngine: MockModelEngine 开始推理: prompt='点击屏幕中心'
D/MockModelEngine: MockModelEngine 推理完成: action=CLICK, confidence=0.82, time=1456ms
D/MainViewModel: 推理完成: action=CLICK, confidence=0.82
D/AgentStateMachine: 状态转换: REASONING_LOCAL → EXECUTING
D/MainViewModel: 执行动作: CLICK
D/AgentStateMachine: 状态转换: EXECUTING → COMPLETED
D/AgentStateMachine: 任务完成，自动回到 IDLE 状态
D/AgentStateMachine: 状态转换: COMPLETED → IDLE
```

### 云端兜底日志示例

```
D/MockModelEngine: MockModelEngine 推理完成: action=CLICK, confidence=0.68, time=1523ms
I/AgentStateMachine: 本地推理置信度不足 (0.68), 转云端兜底
D/AgentStateMachine: 状态转换: REASONING_LOCAL → REASONING_CLOUD
D/MainViewModel: 需要云端兜底
D/AgentStateMachine: 状态转换: REASONING_CLOUD → EXECUTING
```

## 🔧 无障碍服务测试（可选）

### 开启无障碍权限

**步骤**：
1. 打开设备 "设置"
2. 进入 "无障碍" (Accessibility)
3. 找到 "EdgeAgent"
4. 开启服务

**权限说明**：
```
EdgeAgent AI 助手需要无障碍权限来：
1. 捕获屏幕内容进行智能分析
2. 执行自动化操作（点击、滑动等）
3. 提供端侧 AI 辅助功能

所有数据优先在本地处理，保护您的隐私。
```

### 测试无障碍功能

**注意**：当前版本的无障碍服务已实现但未集成到 UI，需要后续开发。

**已实现功能**：
- ✅ UI 树提取
- ✅ 手势执行（点击、滑动、返回等）
- ✅ Bitmap 对象池
- ⏳ 截图功能（需要 Android 11+）

## 🐛 常见问题

### Q1: 应用闪退
**原因**：可能是权限问题或内存不足

**解决**：
1. 查看 Logcat 错误日志
2. 确保设备有足够内存
3. 重新安装应用

### Q2: 状态不更新
**原因**：UI 未正确订阅 StateFlow

**解决**：
1. 检查 Logcat 是否有状态转换日志
2. 重启应用

### Q3: 推理时间过长
**原因**：Mock 引擎模拟了真实推理延迟（1.2-1.8 秒）

**说明**：这是正常现象，真实 VLM 模型推理时间也在这个范围。

### Q4: 无障碍服务无法开启
**原因**：系统限制或权限问题

**解决**：
1. 确保应用已安装
2. 检查系统版本（需要 Android 7.0+）
3. 尝试重启设备

## 📈 性能指标

### 当前性能（Mock 模式）

| 指标 | 数值 |
|------|------|
| 推理时间 | 1.2-1.8 秒 |
| 内存占用 | < 50MB |
| 状态转换延迟 | < 10ms |
| UI 响应时间 | < 16ms (60fps) |

### 预期性能（真实模型）

| 指标 | 数值 |
|------|------|
| 推理时间 | 1-3 秒 (取决于模型大小) |
| 内存占用 | 200-500MB (取决于模型) |
| 状态转换延迟 | < 10ms |
| UI 响应时间 | < 16ms (60fps) |

## 🎯 测试检查清单

- [ ] 应用成功安装
- [ ] 应用成功启动
- [ ] 模型信息正确显示
- [ ] 点击测试按钮有响应
- [ ] 状态正确切换
- [ ] 推理响应正确显示
- [ ] 推理时间在合理范围（1-2 秒）
- [ ] 置信度在 0.6-0.95 范围
- [ ] Logcat 日志正常输出
- [ ] 多次测试无崩溃
- [ ] 内存占用正常

## 📝 测试报告模板

```
测试日期：2026-03-06
测试设备：[设备型号]
Android 版本：[版本号]
应用版本：1.0.0

测试结果：
✅ 基础推理流程 - 通过
✅ 滑动操作 - 通过
✅ 应用操作 - 通过
✅ 云端兜底逻辑 - 通过
✅ 性能指标 - 通过

问题记录：
- 无

建议：
- 后续集成真实模型
- 完善无障碍服务集成
```

## 🚀 下一步

测试通过后，可以：
1. 继续 Phase 3：集成真实 VLM 模型
2. 继续 Phase 4：实现本地 RAG 和云端兜底
3. 优化 UI 界面
4. 添加更多测试用例

---

**祝测试顺利！有问题随时反馈。**
