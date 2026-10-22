# Gateway 与多 WorldView 原型

## 当前能力

Gateway 是客户端和多个 Sector Worker 之间的控制边界，当前维护：

- 每个 Client Session 自己的 `focusSectorId`。
- 每个 Session 对每个 Sector 的视口模式。
- 多 WorldView 的订阅状态。
- 持久化的 Session、Focus 和视口状态。
- Focus Sector 的控制权租约。

视口模式：

| 模式 | 含义 |
| --- | --- |
| `FULL` | 完整视口 |
| `SUMMARY` | 缩小并降低视觉权重 |
| `OFF` | 关闭视口 |

每个 Session 的 Focus Sector 必须是已配置的 Worker，并且不能被该 Session 设为 OFF。

## Client Session

HTTP 请求使用客户端 ID 区分 Session：

```http
X-BM-Client-Id: <client-id>
```

SSE 使用查询参数：

```text
/api/render/stream?clientId=<client-id>
```

每个 Session 保存自己的 Focus 和 WorldView 模式。Gateway 会合并所有 Session 的需求，
实际采样策略按 `FULL > SUMMARY > OFF` 取最高优先级。

## 控制权租约

观察可以多人同时进行，但同一个 Sector 同时只有一个控制者。

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

- 客户端只能为自己的 Focus Sector 申请控制权。
- 不同客户端可以同时控制不同 Sector。
- 租约有效期 15 秒。
- 同一 Client ID 可以续约。
- 其他 Client ID 在租约有效时获得 HTTP 409。
- 获取者可主动释放。
- 租约过期后自动变为空闲。
- 切换客户端 Focus 会清除该客户端旧 Focus 的控制权。
- Render v2 帧包含当前 `controlOwnerId`。

## 输入授权

当前提供一个低风险的测试输入：

```text
POST /api/gateway/input/status/{sectorId}
```

它只会向目标 Worker 发送固定命令 `status`，用于验证完整控制链。

允许执行必须同时满足：

- 请求带有效 Client ID。
- Sector 是该 Client 当前的 Focus。
- 该 Client 持有这个 Sector 的有效控制租约。

结果：

- 无租约：403。
- 观察者：403。
- Focus 不匹配：403。
- 租约持有者：200，并实际向 Worker 发送 `status`。

当前不开放移动、建造或任意服务端命令；这些能力必须经过同一授权函数和后续输入验证。

状态保存在：

```text
bettermindustry/runtime/gateway.properties
```

重启 Orchestrator 后会恢复所有已保存 Session 的 Focus 和 ViewMode。

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
- 顶层包含请求帧的 `clientId`。
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
- 输入路由目前只开放固定 `status` 测试命令，尚未接入移动、建造或任意服务端命令。
- 客户端 ID 尚未绑定真正的用户认证。
- 还没有跨 Sector 传送。
- 采样策略是固定配置，尚未支持按客户端带宽动态调整。
