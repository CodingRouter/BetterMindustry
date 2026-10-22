# Worker 诊断协议

## 目的

`BMSTATUS` 为 Orchestrator、调试器和未来的 Render Tick 提供最小结构化诊断，避免依赖人类可读日志猜测世界是否推进。

## 请求

向 Server Worker 标准输入发送：

```text
bmstatus
```

## 输出

服务端返回一行：

```text
BMSTATUS {"tick":9479.18,"gameTimeSeconds":157.98,"updateId":1796,"wave":1,"waveTimeSeconds":113.47,"paused":false,"gameOver":false,"playing":true,"players":0,"units":0,"enemies":0,"buildings":1,"map":"Mud Flats"}
```

字段：

| 字段 | 含义 |
| --- | --- |
| `tick` | 当前游戏 Tick |
| `gameTimeSeconds` | 当前游戏内时间，单位秒 |
| `updateId` | 服务端非暂停更新计数 |
| `wave` | 当前波次 |
| `waveTimeSeconds` | 距离下一次波次的时间 |
| `paused` | 世界是否暂停 |
| `gameOver` | 是否游戏结束 |
| `playing` | 是否正在运行 |
| `players` | 玩家数量 |
| `units` | 单位数量 |
| `enemies` | 敌方单位数量 |
| `buildings` | 建筑数量 |
| `map` | 地图名称 |

## 当前实现

- `ServerControl` 注册 `bmstatus` 控制台命令。
- Orchestrator 每秒最多请求一次。
- 调试器保存最近一次诊断并显示 `ageMs`。
- 原始 `BMSTATUS` 行不进入可见日志，避免每秒刷屏。

## 未来变化

Render Tick 接入后，诊断应升级为版本化二进制快照或流式协议；当前单行文本格式只用于 M1 调试。
