# Gateway 与多 WorldView 原型

## 当前能力

Gateway 是客户端和多个 Sector Worker 之间的控制边界，当前维护：

- 服务端签发、带随机令牌的 Client Session。
- `GUEST / PLAYER / ADMIN` 三级账户角色。
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

浏览器先建立经过认证的访客 Session：

```http
GET    /api/gateway/session
POST   /api/gateway/session
DELETE /api/gateway/session
```

- `GET` 验证现有 Cookie。
- `POST` 创建新 Session，并轮换旧 Cookie。
- `DELETE` 撤销当前 Session，同时释放它持有的控制租约。

成功创建后，服务端返回 HttpOnly Cookie：

```http
Set-Cookie: bm_session=<256-bit-token>; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800
```

`Client ID` 由服务端生成，不再接受浏览器自行声明。真正的认证凭据是 256 位随机令牌；
状态文件只保存令牌的 SHA-256 哈希，不保存明文。Session 有效期 7 天，最多同时保留 64 个。

除首页和 Session 建立端点外，所有 HTTP API、SSE 和地图资源都要求有效 Session。缺失、
过期或已撤销的凭据返回 HTTP 401。SSE 使用同源 Cookie，不再把 Client ID 放在查询参数中。

本地控制面同时拒绝异常 `Host` 和非白名单 `Origin`：前者防止 DNS rebinding，后者防止
其他本地端口上的网页借用同站 Cookie。非法 Host 返回 HTTP 421，非法 Origin 返回 HTTP 403。

每个 Session 保存自己的 Focus 和 WorldView 模式。Gateway 会合并所有有效 Session 的需求，
实际采样策略按 `FULL > SUMMARY > OFF` 取最高优先级。

## 账户与角色

账户通过以下端点注册和登录：

```http
POST /api/auth/register
POST /api/gateway/session
```

注册和登录使用 `application/x-www-form-urlencoded` 的 `username`、`password` 字段。
登录成功后 Session 绑定账户名，服务端每次认证都会读取账户当前角色。

密码使用 `PBKDF2WithHmacSHA256`、随机 16 字节盐和 210,000 次迭代，保存 256 位派生哈希。
账户文件与 Gateway 状态分离：

```text
bettermindustry/runtime/accounts.properties
```

当前角色权限：

| 能力 | GUEST | PLAYER | ADMIN |
| --- | --- | --- | --- |
| 查看状态、SSE、地图 | 是 | 是 | 是 |
| 修改自己的 Focus/ViewMode | 是 | 是 | 是 |
| 获取控制租约、发送受控输入 | 否 | 是 | 是 |
| 启停 Worker、保存检查点 | 否 | 否 | 是 |
| 原始 Worker 管理命令 | 否 | 否 | 是 |

第一个注册账户成为 ADMIN，后续账户成为 PLAYER。该引导规则只适用于当前仅监听
`127.0.0.1` 的调试阶段；部署到可被远程访问的环境前必须改成受控管理员引导流程。

## 控制权租约

观察可以多人同时进行，但同一个 Sector 同时只有一个控制者。

```text
POST /api/gateway/control/acquire/{sectorId}
POST /api/gateway/control/renew/{sectorId}
POST /api/gateway/control/release/{sectorId}
```

请求必须带有效 Session Cookie。`Client ID` 从认证结果读取，不再从请求头读取。

规则：

- 客户端只能为自己的 Focus Sector 申请控制权。
- 不同客户端可以同时控制不同 Sector。
- 租约有效期 15 秒。
- 同一 Client ID 可以续约。
- 其他 Client ID 在租约有效时获得 HTTP 409。
- 获取者可主动释放。
- 租约过期后自动变为空闲。
- 切换客户端 Focus 会清除该客户端旧 Focus 的控制权。
- 撤销 Session 会立即释放该客户端持有的全部控制权。
- Render v2 帧包含当前 `controlOwnerId`。

## 输入信封与幂等

普通客户端统一使用版本化输入信封：

```http
POST /api/gateway/input/envelope
Content-Type: application/json
Cookie: bm_session=<token>
```

```json
{
  "protocolVersion": 1,
  "commandId": "4cce4a55-08cb-4b05-bc28-c7b486612b49",
  "sectorId": "sector-a",
  "expectedTick": 12345,
  "deadlineTick": 12465,
  "type": "pause",
  "message": ""
}
```

支持的命令：

| 类型 | Worker 命令 | `message` |
| --- | --- | --- |
| `status` | `status` | 忽略 |
| `pause` | `pause on` | 忽略 |
| `resume` | `pause off` | 忽略 |
| `chat` | `say <message>` | 必填 |

`commandId` 是客户端生成的一次性命令标识，当前允许 1-96 位字母、数字、`_` 和 `-`。
客户端因网络结果不明而重试时，必须复用同一个信封和 `commandId`。

幂等规则：

- 同一 Client、Sector、命令类型和聊天内容使用同一 `commandId` 时，返回第一次的回执，不再执行。
- 同一 `commandId` 被另一 Client 使用，或改成分属不同命令，返回 HTTP 409。
- 第一次执行还在 `PENDING` 时，重复请求返回 HTTP 202。
- `APPLIED` 的重复回执返回 HTTP 200，`FAILED` 返回 HTTP 409。
- 回执持久化在 `gateway.properties`，保留 5 分钟且最多 512 条，可跨 Orchestrator 重启。
- 写入使用同目录临时文件加原子替换，避免进程中断留下半个状态文件。
- 启动时发现历史 `PENDING` 会转为 `FAILED`，表示命令可能已发送但无法确认完成；
  该命令 ID 不会再次执行。

当前 `APPLIED` 的准确含义是“Worker 进程的标准输入接受了这条命令”，不是“模拟
Game Tick 已经确认产生最终效果”。pause/resume/chat 的效果通过实际 Worker 诊断验证；
未来移动、建造和单位指令需要 Worker 返回带命令 ID 的执行确认。

模拟时间窗口：

- Worker 当前 Tick 超过 `deadlineTick` 时拒绝，HTTP 409。
- `expectedTick` 最多允许比 Worker 当前 Tick 超前 120 Tick。
- 当前调试客户端使用最近 Render 快照 Tick 作为 `expectedTick`，并把截止时间设为它之后 120 Tick。

聊天内容会去除控制字符、压缩连续空白并把首尾空白去掉，最多 150 个 Unicode 码点。

允许执行必须同时满足：

- 请求 Cookie 对应有效、未撤销的 Session。
- 账户角色至少为 `PLAYER`。
- Sector 是该 Client 当前的 Focus。
- 该 Client 持有这个 Sector 的有效控制租约。

结果：

- 无租约：403。
- 观察者：403。
- Focus 不匹配：403。
- 信封格式、命令类型或聊天长度非法：400。
- 过期 Tick 窗口、运行中的 Worker 不可用或 `commandId` 冲突：409。
- 租约持有者：200，并实际向 Worker 发送对应命令。

成功输入会写入：

- Gateway 内存审计环形缓冲区。
- `bettermindustry/runtime/gateway-audit.log`。

重复投递只返回原回执，不重复写审计。调试器 UI 的 pause、resume 和 chat 均已改用
`/api/gateway/input/envelope`，并在网络结果不明时使用同一信封重试一次。

旧版固定动作端点保留为兼容入口，但不再由普通 UI 使用：

```text
POST /api/gateway/input/status/{sectorId}
POST /api/gateway/input/pause/{sectorId}
POST /api/gateway/input/resume/{sectorId}
POST /api/gateway/input/chat/{sectorId}
```

当前不开放移动、建造或任意服务端命令；这些能力必须经过同一授权函数和后续输入验证。

状态保存在：

```text
bettermindustry/runtime/gateway.properties
```

状态文件同时保存 Session 令牌哈希、Focus、ViewMode 和幂等回执。重启 Orchestrator 后，
仍持有原 Cookie 的浏览器会恢复同一个 Client ID 和它保存的会话状态。
同时恢复幂等回执；已完成命令继续返回 `duplicate=true`，崩溃前未完成的命令返回失败回执。

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
GET    /api/gateway/session
POST   /api/gateway/session
DELETE /api/gateway/session
POST   /api/auth/register
GET    /api/gateway
POST   /api/gateway/focus/{sectorId}
POST   /api/gateway/views/{sectorId}/{FULL|SUMMARY|OFF}
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
- 点击“撤销会话”销毁当前身份并释放控制权。
- 顶部显示当前 `username · role` 并提供登录/注册对话框。
- GUEST 的操作按钮会禁用；PLAYER 可控制；ADMIN 才能管理 Worker。

## 限制

- 当前是 Web 调试客户端，不是最终 Mindustry 桌面客户端。
- 输入路由目前只开放固定的 pause/resume/chat/status 命令；普通 UI 不再提供原始 Worker 命令框。
- `APPLIED` 尚未包含 Worker 的游戏 Tick 级执行确认。
- 当前支持匿名访客与本地用户名/密码账户；OAuth、多因素认证和设备信任尚未实现。
- Session 还没有主动列出全部设备、单独踢出其他设备或管理员强制撤销能力。
- 账户系统还没有密码重置、账户禁用、角色修改和管理员账户管理 UI。
- 登录接口尚未加入按账户/IP 的失败重试限流；开放远程访问前必须补齐。
- 还没有跨 Sector 传送。
- 采样策略是固定配置，尚未支持按客户端带宽动态调整。
