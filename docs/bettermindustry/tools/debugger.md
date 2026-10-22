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

## Worker 配置

测试 Worker 清单位于：

```text
bettermindustry/orchestrator/worlds.properties
```

格式：

```properties
workers=sector-a,sector-b

sector-a.map=wasteland
sector-a.port=16567
sector-a.runtimeDir=bettermindustry/runtime/debugger/sector-a

sector-b.map=veins
sector-b.port=16568
sector-b.runtimeDir=bettermindustry/runtime/debugger/sector-b
```

启动时会校验 Worker ID、端口、运行目录、必需字段和重复值。运行目录必须位于项目目录内。

只验证配置：

```powershell
.\gradlew.bat -p bettermindustry/orchestrator run --args="--validate-config"
```

## 显示内容

- Worker ID、地图和端口。
- 进程状态、PID、运行时长和退出码。
- 实际 TCP 端口监听状态。
- `BMSTATUS` 结构化诊断：Game Tick、游戏时间、波次、暂停、玩家、单位、敌人和建筑数。
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
- Worker 配置目前使用 Java Properties 格式，尚未加入热重载和面板内增删。
- 端口探测只确认 TCP 可连接，尚未执行应用层握手。
- 诊断通过服务端控制命令请求，尚未改成常驻推送通道。
- 生产吞吐、瓶颈和资源变化趋势尚未加入。
- 渲染进程、场景订阅和多视口尚未接入。
