# 本地 Web 调试器

调试器用于观察和操作两个真实 Mindustry Server Worker，不依赖游戏图形窗口。

## 启动

```powershell
.\gradlew.bat -p bettermindustry/orchestrator run
```

打开：

```text
http://127.0.0.1:19090/
```

调试器只监听 `127.0.0.1`，不会暴露到局域网。

## 显示内容

- Worker ID、地图和端口。
- 进程状态、PID、运行时长和退出码。
- 独立检查点是否存在。
- 服务端实时日志。
- Game Tick 与计划中的 Render Tick 管线。

## 控制

- `启动`：启动 Worker；存在检查点时自动加载检查点，否则启动对应地图。
- `停止`：保存 `debugger-checkpoint`，然后发送 `exit`。
- `保存检查点`：发送 `save debugger-checkpoint`。
- `发送`：把文本命令发送到 Worker 标准输入。
- `启动全部` / `停止全部`：批量控制。

## 当前 Worker

| ID | 地图 | 端口 |
| --- | --- | ---: |
| `sector-a` | `wasteland` | 16567 |
| `sector-b` | `veins` | 16568 |

运行目录：

```text
bettermindustry/runtime/debugger/
```

这些目录不进入 Git。

## 当前限制

- 调试器只观察 Game Tick Worker，不渲染地图。
- 端口健康检查尚未实现，当前以进程是否存活和日志为准。
- Worker 固定写在 `WorkerManager` 中，下一步迁移为配置文件。
- 尚未提供 `state.tick`、生产吞吐和单位统计的结构化诊断。
- 渲染进程、场景订阅和多视口尚未接入。
