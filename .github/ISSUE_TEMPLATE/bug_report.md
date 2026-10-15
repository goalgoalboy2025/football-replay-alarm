---
name: Bug 报告
about: 报告一个让 App 工作不正常的问题
title: '[Bug] '
labels: bug
assignees: ''
---

## 出了什么问题

简要描述哪里不对劲。例如："设置了利物浦的提醒，但今早 8 点没收到通知"。

## 复现步骤

1. ...
2. ...
3. 看到 ...

## 期望行为

期望应该发生什么。

## 环境

- 手机型号：
- Android 版本 / 系统皮肤（MagicOS / MIUI / ColorOS 等）：
- App 版本（设置页底部的 "构建时间"）：

## logcat（如有）

用 `adb logcat -v time -s KanqiuAlarm:* KanqiuScheduler:* KanqiuNotify:* KanqiuWorker:* KanqiuReschedule:*` 抓一段相关日志贴进来。

```
（贴 log 到这里）
```

## 截图（如有）

把通知栏、设置页或异常表现的截图拖到这里。
