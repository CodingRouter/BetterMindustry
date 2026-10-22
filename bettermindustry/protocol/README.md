# Protocol

定义控制层、世界进程、渲染进程和未来 BetterMindustry 客户端之间的版本化消息。

当前已实现：

- `bettermindustry.render.v2` 只读 Render Frame。
- Game Tick、游戏时间、更新计数、波次和实体统计。
- `authoritative=false` 和实时世界快照能力标记。
- `focusSectorId`、`controlOwnerId` 与每 Sector 的 `viewMode`。

运行：

```powershell
.\gradlew.bat -p bettermindustry/protocol build
```

当前 Frame 由 Orchestrator 的 `/api/render/snapshot` 和
`/api/render/stream` 提供。Worker 地图 PNG 和实体元数据由
`/api/render/world/{id}/image` 与 `/metadata` 提供。完整客户端素材、实体插值、
能力协商和跨世界事件尚未接入。
