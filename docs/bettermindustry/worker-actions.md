# Worker 管理动作确认

## 目标

Gateway 不再把“命令写入 Worker stdin”当作执行成功。每条受控管理动作携带
`commandId`，Worker 执行后返回同一 ID 的确认。

## 输入线路

```text
bmaction <commandId> <type> [message...]
```

允许类型：

- `status`
- `pause`
- `resume`
- `chat`

`commandId` 只允许 1-96 位字母、数字、`_` 和 `-`。Worker 不接受其他动作类型。

## 输出确认

```text
BMACTION {"protocolVersion":1,"commandId":"...","status":"APPLIED","tick":123.5,"paused":true,"detail":"Game paused."}
```

字段：

| 字段 | 含义 |
| --- | --- |
| `protocolVersion` | 当前为 1 |
| `commandId` | 原样返回输入 ID |
| `status` | `APPLIED` 或 `FAILED` |
| `tick` | 执行动作时的 Worker Tick |
| `paused` | 执行动作后的暂停状态 |
| `detail` | 简短执行说明 |

## Gateway 行为

- `PENDING` 回执在发送动作前持久化。
- 只有收到成功 `BMACTION` 后才写 `APPLIED`。
- Worker 返回失败、进程停止、线路断开或 3 秒超时都写 `FAILED`。
- 回执持久化 `acknowledgementTick` 和 `acknowledgementPaused`。
- 重复投递返回原回执，不再次执行动作。
- 旧版 Worker 不认识 `bmaction` 时会超时并得到 `FAILED`，不会误报成功。

## 安全边界

- 动作类型由 ServerControl 白名单决定，Chat 不经过通用命令拼接。
- 原始 `bmaction` 只适用于本机管理和调试；公网玩家不会直接访问 Worker stdin。
- 当前确认只覆盖管理动作，不代表未来移动、建造和战斗命令的完整世界结果。
