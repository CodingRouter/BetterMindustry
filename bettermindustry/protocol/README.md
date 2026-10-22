# Protocol

定义控制层、世界进程、渲染进程和未来 BetterMindustry 客户端之间的版本化消息。

当前已实现：

- `bettermindustry.render.v1` 只读 Render Frame。
- Game Tick、游戏时间、更新计数、波次和实体统计。
- `authoritative=false` 与 `worldDataAvailable=false` 的显式能力标记。

运行：

```powershell
.\gradlew.bat -p bettermindustry/protocol build
```

当前 Frame 由 Orchestrator 的 `/api/render/snapshot` 和
`/api/render/stream` 提供。完整 Tile/实体增量、能力协商和跨世界事件尚未接入。
