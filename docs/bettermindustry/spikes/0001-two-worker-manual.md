# Spike 0001：两个隔离 Server Worker 手工验证

- 日期：2026-10-22
- 分支：`spike/m1-worker-supervisor`
- 状态：通过

## 目标

在不修改 Mindustry 核心代码的前提下，验证同一个 JVM 外的两个 Server 进程可以：

- 使用不同端口。
- 加载不同地图。
- 隔离配置、日志和存档。
- 无玩家时继续 Tick。
- 一个停止后另一个继续运行。
- 从检查点恢复。

## 环境

- Windows 11
- Temurin JDK 17.0.20.1
- `server/build/libs/server-release.jar`

运行目录：

```text
bettermindustry/runtime/m1-spike/sector-a
bettermindustry/runtime/m1-spike/sector-b
```

测试配置：

| Worker | 地图 | 端口 | autoPause | 测试 autosave |
| --- | --- | ---: | --- | ---: |
| sector-a | Wasteland | 16567 | off | 10 秒 |
| sector-b | Veins | 16568 | off | 10 秒 |

正式配置不会使用 10 秒自动存档；该值只用于快速观察状态变化。

## 启动命令

两个进程均从各自运行目录启动：

```powershell
java -jar D:\猫猫窝\projects\BetterMindustry\server\build\libs\server-release.jar
```

随后分别发送：

```text
config port 16567
config autoPause off
config autosave on
config autosaveSpacing 10
host wasteland survival
```

```text
config port 16568
config autoPause off
config autosave on
config autosaveSpacing 10
host veins survival
```

## 结果

### 端口与地图

- sector-a 输出 `Opened a server on port 16567`。
- sector-b 输出 `Opened a server on port 16568`。
- A 加载 `Wasteland`，B 加载 `Veins`。
- 两边均报告 `No players connected`，仍以约 60 FPS 运行。

### 无玩家 Tick

`status` 显示波次倒计时持续减少，自动存档文件按间隔生成并增大，证明无玩家时世界仍在推进。

### 故障隔离

向 sector-a 发送 `exit` 后：

- sector-a 输出 `Shutting down server.` 并退出。
- sector-b 继续运行。
- sector-b 在 01:13:20 和 01:13:30 继续生成新自动存档。

### 检查点恢复

对 sector-b 执行：

```text
save checkpoint
exit
```

从同一目录重启后执行：

```text
load checkpoint
```

结果：

- 输出 `Save loaded.`。
- 重新监听 `16568`。
- 地图仍为 `Veins`。
- 波次倒计时从保存前的约 220 秒继续到恢复后的 204 秒。
- 仍显示无玩家在线。

## 结论

第一条架构假设成立：**不同工作目录 + 不同端口 + 独立进程可以提供有效的世界隔离。**

M1 可以继续实现自动 Orchestrator，而不需要先修改 Mindustry 核心模拟。

## 后续注意

- 当前构建显示 custom build 警告，后续 Worker 应传入明确构建版本。
- 测试中 10 秒自动存档仅用于验证，正式默认应更长。
- 沙箱内 `Get-NetTCPConnection` 没返回进程监听信息，控制层应使用自己的 TCP 探测并保留服务端日志证据。
- Orchestrator 需要通过 stdin 或本地 socket 可靠发送命令，并等待明确响应，不应只靠延时。
- Worker 日志、PID、退出码和失败原因必须分别持久化。
