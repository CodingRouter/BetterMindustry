# 2026-10-22 M1A 验收日志

## 结论

M1A 的进程级验收通过：

- 两个 Sector Worker 连续运行超过 10 分钟。
- 两个 Worker 的 PID 在测试区间内保持不变。
- 无玩家时 Game Tick 持续推进。
- 两个端口持续监听，地图快照持续更新。
- 强制杀死 Sector A 后，Sector B 继续运行。
- Sector A 能从检查点恢复。
- 重启 Orchestrator 后，两个 Worker 能从检查点恢复。
- 自动诊断命令并发交错问题已修复。

M1B 多世界客户端尚未开始。

## 连续运行

验收区间：2026-10-22 约 12:05 至 12:15。

初始 Worker：

```text
sector-a pid=38860 tick=18126.1
sector-b pid=27524 tick=226156.9
```

601 秒后的 Worker：

```text
sector-a pid=38860 tick=9603.3
sector-b pid=27524 tick=262225.8
```

说明：

- 两个 Java PID 全程未变化。
- 两个端口始终 `portOpen=true`。
- 两次地图 snapshot 始终可用。
- `invalid command` 计数始终为 0。
- Sector A 使用原版 survival，期间因为游戏结束自动轮换地图，所以它的单地图 Tick 会重新计数；进程、端口和 World Worker 没有重启。

## 并发命令修复

验收期间发现 Sector A 周期性出现：

```text
Invalid command. Type 'help' for help.
Command not found. Did you mean "js"?
```

根因：

- 总览轮询和 SSE 渲染流会并发调用 `snapshot()`。
- `bmstatus`、`bmworld` 和控制台命令写入同一个 stdin。
- 写入没有统一串行化，两个短命令会交错。

修复：

- 诊断限流、快照请求和底层 `writeCommand` 使用同一个对象锁。
- 修复后连续 30 秒及完整 10 分钟验收中，无效命令均为 0。

## 故障隔离

在保存 `debugger-checkpoint` 后强制终止：

```text
sector-a pid=38860
```

结果：

```text
sector-a: FAILED, exitCode=1, portOpen=false
sector-b: RUNNING, portOpen=true
```

Sector B 在 5 秒内从 Tick `264377.5` 增长到 `264690.1`。

随后从调试器重新启动 Sector A：

- 加载 `debugger-checkpoint`。
- 新 PID `34560`。
- 端口 `16567` 重新监听。
- Tick 从保存的 `10212.7` 附近恢复到 `10831.7` 并继续推进。

## 控制层恢复

优雅停止两个 Worker：

```text
sector-a: STOPPED, exitCode=0, checkpoint=true
sector-b: STOPPED, exitCode=0, checkpoint=true
```

关闭并重新启动 Orchestrator 后：

```text
sector-a: RUNNING, new pid=25916, tick=12176.4
sector-b: RUNNING, new pid=35388, tick=267016.4
```

两个端口重新监听，两个 Worker 均从检查点恢复。

## 渲染与 UI 修复

- Headless Server 通过确定性调色板生成真实地图 PNG。
- 浏览器通过内嵌 data URL 加载地图，避免图片请求链路问题。
- 修复重复 `data-field` 导致图片地址被赋给文字元素的问题。
- 修复旧页面缺少 `[hidden]` 规则导致“等待地图快照”占位文字残留的问题。
- 新 UI 使用大尺寸地图预览、独立指标卡和顶部状态汇总。

## 收工状态

- 两个 Worker 已优雅停止，退出码为 0。
- 两个检查点均存在。
- 调试器已停止。
- 工作区不包含 runtime、存档或日志。

## 下一步

M1A 进程级验收已结束，下一阶段是 M1B：

- Gateway 按 `sectorId` 多路复用世界订阅。
- 一个客户端维护多个 WorldView。
- 玩家控制权只属于一个 Focus Sector。
- 先支持独立视口，不要求无缝跨区块拼接。
