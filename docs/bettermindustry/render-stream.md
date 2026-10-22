# Render Tick 快照流

## 当前目标

在不把渲染逻辑塞回权威 Server 的前提下，建立 Render Tick 读取 Game Tick 的边界。

当前实现的是 **Render Frame 骨架**：

- Game Tick Worker 仍由独立进程运行。
- Orchestrator 从 Worker 获取最近一次结构化诊断。
- 浏览器通过 SSE 订阅只读快照。
- 浏览器使用自己的 `requestAnimationFrame` 渲染循环。
- 当前快照不包含完整 Tile、建筑和实体状态，因此还不能还原 Mindustry 地图画面。

## HTTP 接口

### 单帧

```text
GET /api/render/snapshot
```

适合调试和渲染进程首次握手。

### 流

```text
GET /api/render/stream
Content-Type: text/event-stream
```

当前约每 50 ms 发送一个 `render` 事件，约 20 Hz。浏览器可以按自己的帧率消费和插值。

## 帧字段

```json
{
  "protocol": "bettermindustry.render.v1",
  "protocolVersion": 1,
  "sequence": 12,
  "renderTimeMillis": 1792638436685,
  "authoritative": false,
  "worldDataAvailable": false,
  "sectors": [
    {
      "id": "sector-a",
      "map": "Mud Flats",
      "port": 16567,
      "state": "RUNNING",
      "alive": true,
      "portOpen": true,
      "gameTick": 14270.1,
      "gameTimeSeconds": 237.8,
      "updateId": 1796,
      "wave": 1,
      "paused": false,
      "players": 0,
      "units": 0,
      "enemies": 0,
      "buildings": 1,
      "diagnosticAgeMillis": 178
    }
  ]
}
```

`authoritative=false` 表示渲染侧不能修改世界；`worldDataAvailable=false` 表示当前还不包含地图几何和实体增量。

## 浏览器调试显示

打开 `http://127.0.0.1:19090/` 后，管线节点会显示：

- `Game Tick`：快照序列和最新 Tick。
- `Render Tick`：浏览器自身渲染 FPS 和收到的快照频率。

这验证了两个时钟确实分离，但还不是完整的 Mindustry 地图渲染器。

## 下一阶段

- 加入初始 Tile/建筑/实体世界快照。
- 加入按 Tick 的实体增量。
- 定义断线重连和快照追赶。
- 渲染进程加入插值、订阅上限和背压。
- 再把浏览器 PROBE 替换为真正的桌面渲染进程。
