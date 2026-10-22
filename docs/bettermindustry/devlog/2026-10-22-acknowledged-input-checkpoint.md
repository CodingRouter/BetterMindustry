# 2026-10-22 M1B-AcknowledgedInput 检查点

## 结论

受控输入现在具备 Worker 执行确认。Gateway 不再把 stdin 写入成功当成最终成功，回执会保存
Worker 执行时的 Tick 和 paused 状态。

## 完成内容

- ServerControl 新增 `bmaction <commandId> <type> [message]`。
- Worker 只允许 `status / pause / resume / chat`。
- Worker 输出 `BMACTION`，携带协议版本、命令 ID、状态、Tick、paused 和详情。
- ManagedWorker 维护并发待确认动作表，3 秒超时。
- Worker 退出时将所有待确认动作完成为 `FAILED`。
- Gateway 收到成功确认后才写 `APPLIED`。
- 幂等回执新增并持久化 `acknowledgementTick` 与 `acknowledgementPaused`。
- 重复投递保留原确认现场。

## 成功验收

```text
status -> APPLIED，ackTick=7334.877...
pause  -> APPLIED，ackTick=7337.973...，ackPaused=true
chat   -> APPLIED，ackPaused=true
resume -> APPLIED，ackPaused=false
重复 pause -> duplicate=true，保留第一次 ackPaused=true
```

## 失败验收

停止 `sector-a` 后发送新的 status 输入：

```text
Worker exit code -> 0
receipt status   -> FAILED
detail           -> Worker sector-a is not running
ack tick         -> -1
ack paused       -> null
```

## 安全测试

```text
GUEST 输入 -> 403
未知动作 -> 400
非法 commandId -> 400
151 字符聊天 -> 400
聊天注入 hello"; exit -> 200 APPLIED
注入后 Worker alive=true, portOpen=true
重复 ID 改成另一动作 -> 409
Worker 停止后输入 -> 409，回执状态 FAILED
```

聊天内容只作为 `bmaction chat` 的消息参数传进白名单动作处理器，没有进入通用命令执
行路径。

## 构建

- `server:dist`：通过。
- `bettermindustry/orchestrator clean build`：通过。
- Protocol 5 个测试、Orchestrator 19 个测试，共 24 个测试全部通过。

## 检查点边界

本检查点只确认管理层动作。移动、建造、单位和战斗输入仍未开放，下一阶段必须在相同确认链
上定义世界状态语义、能力协商和限流。
