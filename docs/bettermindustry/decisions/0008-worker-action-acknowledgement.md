# ADR-0008：受控动作必须等待 Worker 确认

- 状态：已接受，第一阶段已实现
- 日期：2026-10-22

## 背景

之前的 `APPLIED` 只表示命令写入了 Worker 标准输入。进程可能已经停止、命令可能解析失败，
或者响应尚未执行；把 stdin 写入成功当作动作成功，不足以支撑未来的移动和建造命令。

## 决策

Gateway 通过结构化线路命令发送管理动作：

```text
bmaction <commandId> <type> [message]
```

Worker 执行后返回同一 ID：

```text
BMACTION {protocolVersion, commandId, status, tick, paused, detail}
```

只有成功确认到达后才写 `APPLIED`。确认超时、Worker 失败或进程退出写 `FAILED`。

第一阶段允许：

- `status`
- `pause`
- `resume`
- `chat`

## 为什么仍保留幂等回执

确认解决了“动作是否被 Worker 执行”，但没有解决网络重试。`commandId` 同时服务：

- 关联 Worker 确认。
- 防止重复动作。
- 跨 Orchestrator 重启保留执行现场。

## 后果

优点：

- `APPLIED` 不再等同于管道写入。
- 错误和超时可见。
- 回执包含执行 Tick 和 pause 状态。
- 为未来 Builder/Unit 动作提供统一确认框架。

代价：

- 每个动作最多等待 3 秒。
- 当前 Worker 控制线路仍是本机进程 stdin/stdout，不是网络协议。
- 需要继续增加能力协商、版本兼容和超时配置。
