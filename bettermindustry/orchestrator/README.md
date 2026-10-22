# Orchestrator Debugger

负责启动两个隔离的 Mindustry Server Worker，并提供一个只监听本机的简单 Web 调试器。

启动：

```powershell
.\gradlew.bat -p bettermindustry/orchestrator run
```

浏览器打开：

```text
http://127.0.0.1:19090/
```

当前功能：

- 从 `worlds.properties` 加载世界清单，并校验重复 ID、端口和目录。
- 显示两个 Worker 的状态、地图、端口、PID、运行时长和日志。
- 通过真实 TCP 连接探测 Worker 端口。
- 分别启动、停止和保存两个 Worker。
- 向 Worker 标准输入发送任意服务端命令。
- 启动时自动启动 `sector-a` 和 `sector-b`。
- 运行目录位于 `bettermindustry/runtime/debugger/`，不会进入 Git。

当前调试器只观察 Game Tick。独立 Render Tick 进程、场景订阅和画面合成属于下一阶段。

验证配置但不启动服务：

```powershell
.\gradlew.bat -p bettermindustry/orchestrator run --args="--validate-config"
```
