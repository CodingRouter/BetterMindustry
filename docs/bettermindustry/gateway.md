# Gateway 与多 WorldView 原型

## 当前能力

Gateway 是客户端和多个 Sector Worker 之间的控制边界，当前维护：

- 唯一 `focusSectorId`。
- 每个 Sector 的视口模式。
- 多 WorldView 的订阅状态。

视口模式：

| 模式 | 含义 |
| --- | --- |
| `FULL` | 完整视口 |
| `SUMMARY` | 缩小并降低视觉权重 |
| `OFF` | 关闭视口 |

Focus Sector 必须是已配置的 Worker，并且不能被设为 OFF。

## HTTP API

```text
GET  /api/gateway
POST /api/gateway/focus/{sectorId}
POST /api/gateway/views/{sectorId}/{FULL|SUMMARY|OFF}
```

示例：

```text
POST /api/gateway/focus/sector-b
POST /api/gateway/views/sector-a/SUMMARY
```

## Render Frame

协议已升级为 `bettermindustry.render.v2`：

- 顶层包含 `focusSectorId`。
- 每个 Sector 包含 `viewMode`。
- 帧仍然只读，`authoritative=false`。

## 当前客户端

浏览器调试器充当第一版多 WorldView Client：

- 每个 Worker 显示为一张 WorldView。
- FOCUS 卡片高亮。
- OBSERVE 卡片保持只读。
- `SUMMARY` 视口缩小地图。
- `OFF` 视口隐藏地图预览。
- 点击“设为主控”切换 Focus。
- 点击“切换视口”循环切换模式。

## 限制

- 当前是 Web 调试客户端，不是最终 Mindustry 桌面客户端。
- 还没有真正的玩家输入路由；Focus 目前只表示控制和观察身份。
- Gateway 状态保存在内存中，重启后恢复默认 Focus。
- 还没有多用户、权限、连接租约和跨 Sector 传送。
- SUMMARY/OFF 还没有降低 Worker 快照频率。
