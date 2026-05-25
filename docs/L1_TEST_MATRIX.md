# L1 真机验收矩阵

本文档用于 Redmi K60 真机验收 L1 基础能力。所有业务任务必须从 VisionAgent App 内输入并执行；ADB 只允许用于安装、保活、权限准备、抓日志和拉取 Trace。

## 验收前准备

```bash
./keep_device_awake.sh start
./dev_bootstrap_permissions.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tencent.edgeagent/.ui.MainActivity
```

每条用例执行后：

```bash
./view_logs.sh --replay
```

记录：

- 是否执行成功。
- 当前包名是否符合预期。
- AgentTrace 是否包含模型输出、策略改写、执行结果。
- 是否误触发 L2 或高风险动作。

## P0 必测

| 编号 | 用户输入 | 预期动作 | 验收标准 |
| --- | --- | --- | --- |
| L1-P0-001 | 返回 | `BACK` | 当前页面返回上一层或关闭遮罩 |
| L1-P0-002 | 回到桌面 | `HOME` | 返回系统桌面 |
| L1-P0-003 | 打开最近任务 | `RECENTS` | 出现最近任务界面 |
| L1-P0-004 | 关闭键盘 | `BACK` | 软键盘收起 |
| L1-P0-005 | 调高音量 | `DEVICE_CONTROL/VOLUME_UP` | 系统音量 UI 出现，媒体音量增加 |
| L1-P0-006 | 调低音量 | `DEVICE_CONTROL/VOLUME_DOWN` | 系统音量 UI 出现，媒体音量减少 |
| L1-P0-007 | 打开相机 | `OPEN_APP/com.android.camera` | 当前包名切到相机 |
| L1-P0-008 | 打开设置 | `OPEN_APP/com.android.settings` | 当前包名切到设置 |
| L1-P0-009 | 打开 WiFi 设置 | `DEVICE_CONTROL/WIFI_SETTINGS` | 进入 Wi-Fi 设置页 |
| L1-P0-010 | 打开蓝牙设置 | `DEVICE_CONTROL/BLUETOOTH_SETTINGS` | 进入蓝牙设置页 |

## P1 应测

| 编号 | 用户输入 | 预期动作 | 验收标准 |
| --- | --- | --- | --- |
| L1-P1-001 | 打开通知栏 | `DEVICE_CONTROL/NOTIFICATIONS_SHADE` | 通知栏展开 |
| L1-P1-002 | 打开快捷设置 | `DEVICE_CONTROL/QUICK_SETTINGS` | 快捷设置或控制中心展开 |
| L1-P1-003 | 关闭通知栏 | `DEVICE_CONTROL/DISMISS_SYSTEM_SHADE` | 系统遮罩关闭 |
| L1-P1-004 | 静音音量 | `DEVICE_CONTROL/VOLUME_MUTE` | 媒体音量静音 |
| L1-P1-005 | 取消静音音量 | `DEVICE_CONTROL/VOLUME_UNMUTE` | 媒体音量取消静音 |
| L1-P1-006 | 播放暂停 | `DEVICE_CONTROL/MEDIA_PLAY_PAUSE` | 媒体播放状态切换 |
| L1-P1-007 | 下一首 | `DEVICE_CONTROL/MEDIA_NEXT` | 媒体下一首命令发出 |
| L1-P1-008 | 打开声音与触感设置 | `DEVICE_CONTROL/SOUND_SETTINGS` | 进入声音设置页 |
| L1-P1-009 | 打开壁纸设置 | `DEVICE_CONTROL/WALLPAPER_SETTINGS` | 进入壁纸入口或壁纸选择器 |
| L1-P1-010 | 打开日期时间设置 | `DEVICE_CONTROL/DATE_TIME_SETTINGS` | 进入日期时间设置页 |
| L1-P1-011 | 打开定位设置 | `DEVICE_CONTROL/LOCATION_SETTINGS` | 进入定位设置页 |
| L1-P1-012 | 打开电池设置 | `DEVICE_CONTROL/BATTERY_SETTINGS` | 进入电池设置页 |

## L2 边界回归

这些输入不能被 L1 直接接管，应进入模型多轮任务或等待后续 L2 策略：

| 编号 | 用户输入 | 预期 |
| --- | --- | --- |
| L1-B-001 | 把时区改为阿根廷 | 不返回 L1 确定性动作 |
| L1-B-002 | 开启蓝牙并连接某个设备 | 不返回 L1 确定性动作 |
| L1-B-003 | 设置自己的壁纸 | 不返回 L1 确定性动作 |
| L1-B-004 | 打开微信给 Nick 发送消息：你好 | 不返回 L1 确定性动作，进入微信草稿策略 |

## 通过标准

- P0 全部通过。
- P1 失败项有 AgentTrace 可回放，并能定位到模型、策略、执行或 ROM 限制。
- 边界回归全部通过，不把 L2/L3/L4 任务误判为 L1。
- 失败样本写入 RAG 或专项策略待办。

