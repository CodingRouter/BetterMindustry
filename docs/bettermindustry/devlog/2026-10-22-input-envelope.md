# 2026-10-22 M1B Input Envelope

## 结论

受控输入从固定动作 URL 升级为带 Tick 窗口和 `commandId` 的版本化信封。客户端可以安全地
重试结果不明的命令，Gateway 会返回第一次回执，而不会重复执行游戏侧输入。

## 完成内容

- `InputCommandType` 定义 `status / pause / resume / chat`。
- `InputEnvelope v1` 校验协议版本、命令 ID、Sector、Tick 窗口和命令类型。
- 聊天输入先清理控制字符与连续空白，再按 Unicode 码点限制为 150。
- Gateway 维护 `PENDING / APPLIED / FAILED` 回执，保留 5 分钟且最多 512 条。
- 同一 Client、Sector、类型和消息复用命令 ID 时直接返回原回执。
- 命令 ID 被其他 Client 使用，或复用成另一条命令时返回 HTTP 409。
- 重复回执在授权检查前返回，因此租约刚过期也不会丢失已经完成的执行结果。
- 正常输入映射到受控 Worker 命令，成功执行才写入审计。
- 调试器 UI 改为统一发送 `/api/gateway/input/envelope`。
- UI 的网络重试复用同一个 `commandId`，不会生成第二条业务命令。

## HTTP 结果

| 情况 | HTTP |
| --- | ---: |
| 首次执行成功 | 200 |
| 重复请求命中 `APPLIED` 回执 | 200 |
| 重复请求命中 `PENDING` 回执 | 202 |
| Worker 执行失败或命令 ID 冲突 | 409 |
| 无 Focus 或无有效租约 | 403 |
| 信封格式、命令类型或聊天长度非法 | 400 |

## 运行验收

使用两个真实 Worker 在 `19090 / 16567 / 16568` 端口完成：

```text
pause envelope -> APPLIED，Worker paused=true
resume envelope -> APPLIED，Worker paused=false
chat "  hello\n\nstatus  " -> APPLIED，规范化 detail="hello status"
同 commandId 重复投递 -> duplicate=true，HTTP 200
同 commandId 改成 resume -> HTTP 409
同 commandId 改成另一句 chat -> HTTP 409
新 commandId 使用过期 deadline -> HTTP 409
释放租约后发送新命令 -> HTTP 403
其他 Client 使用已有 commandId -> HTTP 409
Sector 不是 Client Focus -> HTTP 403
151 字符聊天 -> HTTP 400
```

审计文件只出现三个成功业务命令：

```text
test-owner  sector-a  pause   on
test-owner  sector-a  chat    hello status
test-owner  sector-a  resume  off
```

重复、冲突、过期和拒绝请求均未写审计。

## 构建与 UI

- `bettermindustry/protocol clean build`：通过，包含 5 个 Input Envelope 测试。
- `bettermindustry/orchestrator clean build`：通过。
- 实际 HTTP 服务返回的页面只引用新 envelope 端点，旧 pause/resume/chat URL 为零引用。
- 本机无头 Edge 在启动时以 `0xC0000005` 崩溃，因此本轮没有取得自动浏览器截图；
  UI 的端到端请求已通过相同 HTTP 接口和实际 Worker 验证。

## 收工状态

- 两个 Worker 优雅退出，退出码均为 0，检查点存在。
- `19090 / 16567 / 16568` 无监听残留。
- 测试用 `gateway.properties`、`gateway-audit.log` 和临时 Edge 目录已清理。

## 已知限制

- 本阶段回执只存在 Orchestrator 内存中；后续已在
  [持久化输入回执](2026-10-22-durable-input-receipts.md) 中补齐跨重启恢复。
- 本阶段 Client ID 仍是请求声明值；后续已在
  [认证访客 Session](2026-10-22-authenticated-sessions.md) 中改为服务端签发令牌。
- 当前仅开放四种管理/聊天命令，尚未开放移动、建造和单位操作。
