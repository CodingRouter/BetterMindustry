# Gateway 与多 WorldView 原型

## 当前能力

Gateway 是客户端和多个 Sector Worker 之间的控制边界，当前维护：

- 唯一 `focusSectorId`。
- 每个 Sector 的视口模式。
- 多 WorldView 的订阅状态。
- 持久化的 Focus 和视口状态。
- Focus Sector 的控制权租约。

视口模式：

| 模式 | 含义 |
| --- | --- |
| `FULL` | 完整视口 |
| `SUMMARY` | 缩小并降低视觉权重 |
| `OFF` | 关闭视口 |

Focus Sector 必须是已配置的 Worker，并且不能被设为 OFF。

## 控制权租约

观察可以多人同时进行，但同一个 Focus Sector 同时只有一个控制者。

```text
POST /api/gateway/control/acquire/{sectorId}
POST /api/gateway/control/renew/{sectorId}
POST /api/gateway/control/release/{sectorId}
```

请求必须带：

```http
X-BM-Client-Id: <client-id>
```

规则：

- 只有 Focus Sector 可以申请控制权。
- 租约有效期 15 秒。
- 同一 Client ID 可以续约。
- 其他 Client ID 在租约有效时获得 HTTP 409。
- 获取者可主动释放。
- 租约过期后自动变为空闲。
- 切换 Focus 会清除旧 Focus 的控制权。
- Render v2 帧包含当前 `controlOwnerId`。

状态保存在：

```text
bettermindustry/runtime/gateway.properties
```

重启 Orchestrator 后会恢复 Focus 和所有 ViewMode。

## 采样频率

视口模式不仅影响 UI，也控制 Worker 诊断和地图快照频率：

| 模式 | 状态诊断 | 地图快照 |
| --- | ---: | ---: |
| `FULL` | 1 秒 | 2 秒 |
| `SUMMARY` | 2.5 秒 | 5 秒 |
| `OFF` | 5 秒 | 停止 |

OFF 仍保留低频健康检查，但不会继续请求 `bmworld`。

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
- 还没有真正的玩家输入路由；控制租约目前只是权限边界。
- 还没有用户认证和跨 Sector 传送。
- 采样策略是固定配置，尚未支持按客户端带宽动态调整。
