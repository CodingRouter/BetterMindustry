# Protocol

定义控制层、世界进程、渲染进程和未来 BetterMindustry 客户端之间的版本化消息。

当前已实现：

- `InputEnvelope v1`：Client 到 Gateway 的版本化受控输入和幂等命令标识。
- `status / pause / resume / chat` 四种输入命令。
- Tick 时间窗口、聊天清理和 1-150 Unicode 码点限制。
- `bettermindustry.render.v2` 只读 Render Frame。
- Game Tick、游戏时间、更新计数、波次和实体统计。
- `authoritative=false` 和实时世界快照能力标记。
- `clientId`、`focusSectorId`、`controlOwnerId` 与每 Sector 的 `viewMode`。

运行：

```powershell
.\gradlew.bat -p bettermindustry/protocol build
```

当前 Frame 由 Orchestrator 的 `/api/render/snapshot` 和
`/api/render/stream` 提供。Worker 地图 PNG 和实体元数据由
`/api/render/world/{id}/image` 与 `/metadata` 提供。完整客户端素材、实体插值、
能力协商和跨世界事件尚未接入。

输入信封由 `InputEnvelope` 和 `InputCommandType` 定义，HTTP 入口为
`POST /api/gateway/input/envelope`。协议测试：

```powershell
.\gradlew.bat -p bettermindustry/protocol test
```
