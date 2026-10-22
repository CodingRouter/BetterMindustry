# 2026-10-22 认证访客 Session

## 结论

Client ID 从“客户端随便声明”升级为“服务端签发并由随机令牌认证”。控制租约、输入审计和
幂等回执现在都绑定到可撤销的 Session，而不是可伪造的请求头。

## 完成内容

- 新增 `AuthSessionBook`，生成 256 位随机不透明令牌。
- Client ID 使用 `client-<uuid>`，由服务端生成。
- 状态文件只保存令牌 SHA-256 哈希、创建时间和过期时间。
- Session 固定 7 天有效，最多同时保留 64 个。
- `GET/POST/DELETE /api/gateway/session` 分别负责验证、签发和撤销。
- Cookie 使用 `HttpOnly` 和 `SameSite=Strict`。
- 浏览器 SSE 改为自动携带同源 Cookie，不再使用 `clientId` 查询参数。
- 调试器移除 localStorage Client ID 和 `X-BM-Client-Id`。
- 新增“撤销会话”按钮。
- 除首页和 Session API 外，HTTP API、SSE 和地图文件全部要求 Session。
- 撤销 Session 会同步删除 Focus/ViewMode 并释放控制租约。
- Host 必须为 `127.0.0.1:19090` 或 `localhost:19090`。
- 非 GET/HEAD 请求如果带 Origin，必须是同源地址。

## 安全属性

```text
明文令牌：只在创建响应 Set-Cookie 和服务端内存中出现
磁盘：只保存 SHA-256(token)
浏览器 JavaScript：无法读取 HttpOnly Cookie
身份：服务端从令牌哈希反查 Client ID
撤销：删除哈希和 Session，立即返回 401
```

这仍不是正式用户账号。当前 Session 是自动签发的访客身份，后续需要账户登录、设备管理和
管理员权限层。

## 自动测试

`AuthSessionBookTest` 覆盖：

- Session 签发与令牌认证。
- 未知、非法和已撤销令牌被拒绝。
- Properties 序列化只保存哈希，并可跨加载恢复认证。
- 7 天过期和 64 Session 容量上限。

## 运行验收

```text
无 Cookie GET overview -> 401
伪造 X-BM-Client-Id GET overview -> 401
POST session -> 200，Cookie HttpOnly=true
合法 Cookie GET overview -> 200，Client ID 与 Session 一致
Session A 持有 sector-a -> Session B 获取返回 409
撤销 Session A -> 200
Session B 再获取 sector-a -> 200
撤销后的 Session B Cookie -> 401
状态文件中明文 token -> 不存在
状态文件中 tokenHash -> 存在
完整重启 Orchestrator 后原 token -> 恢复同一 Client ID
撤销恢复后的 token -> 后续请求 401
非法 Host -> 421
跨源 Origin -> 403
同源 Origin -> 200
```

## 构建

- `bettermindustry/protocol clean build`：通过。
- `bettermindustry/orchestrator clean build`：通过，9 个测试全部通过。
- Protocol 与 Orchestrator 共 14 个测试全部通过。

## 收工状态

- 两个 Worker 均优雅退出，退出码为 0，检查点存在。
- `19090 / 16567 / 16568` 无监听残留。
- 测试 Gateway 状态和审计文件在验收后清理。
