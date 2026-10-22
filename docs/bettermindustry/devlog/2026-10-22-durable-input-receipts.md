# 2026-10-22 持久化输入回执

## 结论

输入幂等从进程内能力升级为可跨 Orchestrator 重启的能力。已经成功执行的命令在重启后
仍返回原回执；崩溃前无法确认完成的命令转为失败，既不会假装成功，也不会重复执行。

## 完成内容

- 新增独立 `InputReceiptBook`，负责回执查询、冲突检测、完成状态、过期和容量控制。
- 回执与 Client Session 一起写入 `bettermindustry/runtime/gateway.properties`。
- 状态文件先写同目录临时文件，再使用 `ATOMIC_MOVE` 替换正式文件。
- 不支持原子移动时回退到普通替换，并清理临时文件。
- 状态写入失败会向调用方报错，不再静默忽略。
- 留存窗口为 5 分钟，最多 512 条。
- 加载历史 `PENDING` 时改为 `FAILED`，详情为
  `orchestrator restarted before input completion`。

## 崩溃语义

```text
请求进入
  -> 校验权限和 Tick
  -> 写入 PENDING
  -> 发送 Worker 命令
  -> 写入 APPLIED / FAILED
  -> 返回 HTTP 回执
```

如果在写入 `PENDING` 后、写入最终状态前崩溃，重试同一个 `commandId` 会得到失败回执。
客户端应使用新的 `commandId` 明确发起下一次操作，而不是猜测原命令是否执行。

这个选择提供“不重复执行”保证。它可能牺牲少数不确定命令的自动重试能力，但比重复暂停、
重复聊天或未来重复建造更安全。

本阶段的 `APPLIED` 表示命令已写入 Worker 标准输入，不表示世界在某个 Game Tick 已确认
最终效果。后续已在
[Worker 动作确认](2026-10-22-acknowledged-input-checkpoint.md) 中加入带原始 `commandId`
的 `BMACTION` 执行回执。

## 自动测试

`InputReceiptBookTest` 覆盖：

- 重复命令返回第一次的不可变回执。
- 不同 Client 或不同聊天内容复用命令 ID 会被拒绝。
- `APPLIED` 回执经过 Properties 序列化后可恢复。
- 历史 `PENDING` 在重启加载后转为 `FAILED`。
- 5 分钟过期和 512 条容量上限。

## 运行验收

```text
首次 status commandId=restart-receipt-1 -> APPLIED，duplicate=false
优雅停止全部 Worker 和 Orchestrator
重新启动 Orchestrator
同一 commandId -> APPLIED，duplicate=true
审计记录数量 -> 1
```

Gateway 状态文件中可看到该命令的 Client、Sector、类型、状态、详情和时间戳。

## 构建

- `bettermindustry/protocol test`：通过。
- `bettermindustry/orchestrator build`：通过。
- Protocol 与 Orchestrator 的全部现有测试通过。

## 收工状态

- 两个 Worker 均优雅退出，退出码为 0，检查点存在。
- `19090 / 16567 / 16568` 无监听残留。
- 测试 Gateway 状态和审计文件已清理。
