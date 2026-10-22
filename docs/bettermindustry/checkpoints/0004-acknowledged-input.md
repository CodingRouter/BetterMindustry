# M1B-AcknowledgedInput 检查点

- 状态：已完成
- 日期：2026-10-22

## 目标

消除 `APPLIED` 只表示 stdin 写入成功的歧义，在开放更复杂世界输入前建立 Worker 执行确认。

## 退出条件

- [x] Worker 新增白名单管理动作 `bmaction`。
- [x] Worker 返回带 `commandId`、Tick、paused 和状态的 `BMACTION`。
- [x] Gateway 等待确认后才写 `APPLIED`。
- [x] 超时、断线、进程停止和 Worker 失败都写 `FAILED`。
- [x] 幂等回执持久化确认 Tick 与 paused。
- [x] 重复投递保留第一次确认现场。
- [x] ServerControl 和 Orchestrator 构建通过。
- [x] 真实 Worker 完成 status/pause/chat/resume 成功验收。
- [x] Worker 停止时输入回执落为 `FAILED`。
- [x] 安全测试、文档和 PR 准备完成。

## 暂不包含

- 移动、建造、单位和战斗输入。
- 跨进程动作总线。
- 可配置的动作超时与能力协商。
- 高延迟网络下的重试策略。

## 安全测试

```text
GUEST 发送输入 -> 403
未知动作 destroy -> 400
commandId 含空格 -> 400
151 字符聊天 -> 400
聊天内容 hello"; exit -> APPLIED
注入后 Worker alive/portOpen -> true / true
同 commandId 改成 resume -> 409
Worker 停止后 status -> 409，持久化 FAILED
```
