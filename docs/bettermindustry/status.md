# 当前状态与交接

- 最后更新：2026-10-23
- 当前分支：`feat/m1b-desktop-multiview`
- 当前阶段：M1B-DesktopMultiView 检查点已完成
- 当前里程碑：M1A 完成，M1B 原型进行中

## 已完成

- 基于 Mindustry `v160.7` 建立本地分支。
- 上游提交：`17012d61d9e4c3cbd9820d87e81c24495486ac53`。
- 在线主干采用 v160.7 源码快照根提交，完整历史保存在本地归档分支。
- 335 个上游测试全部通过。
- `server:dist`、`desktop:dist` 构建成功。
- 服务端 JAR 已实际启动并正常退出。
- 项目入口、路线图、总体架构、风险、ADR 和开发流程已建立。
- 本地 Web 调试器可显示两个真实 Worker 的状态、日志和命令。
- Worker 列表已改为配置驱动，并加入真实 TCP 端口探测。
- Worker 已提供 `bmstatus` 结构化 Tick 诊断，调试器实时显示。
- 已提供 `bettermindustry.render.v1` 单帧端点和 20 Hz SSE 快照流。
- Server Worker 已生成地图 PNG、单位和建筑 JSON，浏览器 Canvas 已能显示真实地图缩略图。
- 两个 Worker 连续运行超过 10 分钟，故障隔离和控制层恢复通过。
- Gateway 支持唯一 Focus Sector 和 FULL/SUMMARY/OFF 视口模式。
- Render 协议升级为 `bettermindustry.render.v2`。
- Gateway 状态跨 Orchestrator 重启恢复。
- FULL/SUMMARY/OFF 已实际控制诊断和地图快照频率。
- Focus Sector 支持 15 秒控制权租约、竞争拒绝、续约和自动过期。
- 每个 Client ID 独立维护 Focus 和 WorldView，Gateway 合并有效采样模式。
- Focus 控制租约已接入固定 `status` 测试输入，观察者和无租约请求会被拒绝。
- 受控输入支持 pause/resume/chat，并写入 Gateway 审计。
- 普通 UI 已移除绕过 Gateway 的原始命令框。
- 新增版本化 Input Envelope 和 `commandId` 幂等回执。
- 输入校验包含协议版本、Ticks、命令类型、聊天内容和 Retry 冲突。
- `pause / resume / chat` 已切换到 `/api/gateway/input/envelope`。
- 重复输入只返回原回执，不再次执行或重复写审计。
- 输入回执已持久化，并通过临时文件原子替换写入 Gateway 状态文件。
- 已验证完整停止并重启 Orchestrator 后，同一 `commandId` 仍返回 `duplicate=true`。
- 重启时遗留的 `PENDING` 回执会转为 `FAILED`，保留“不重复执行”保证。
- 服务端生成 256 位随机 Session 令牌，Client ID 不再由请求头自行声明。
- Cookie 使用 HttpOnly 和 SameSite=Strict，状态文件只保存令牌 SHA-256 哈希。
- Session 支持建立、恢复、撤销和 7 天过期，最多保留 64 个。
- 撤销 Session 会立即释放它持有的控制租约。
- HTTP API、SSE 和地图资源全部接入 Session 认证。
- 控制面校验 Host 和写请求 Origin，拒绝 DNS rebinding 与跨站写请求。
- Web 调试器不再使用 localStorage Client ID，并新增“撤销会话”按钮。
- 新增 GUEST/PLAYER/ADMIN 账户角色和登录/注册流程。
- 密码使用随机盐与 210,000 次 PBKDF2-HMAC-SHA256，不保存明文。
- Session 绑定用户名，账户和角色可跨 Orchestrator 重启恢复。
- GUEST 只能观察，PLAYER 可以控制 Sector，ADMIN 才能管理 Worker。
- 调试器按角色禁用操作按钮，并显示当前用户名与角色。
- 登录失败按地址和用户名限制为 5 次/5 分钟，超限返回 429 与 `Retry-After`。
- Session 保存设备标签，账户可列出、撤销指定设备或退出其他设备。
- ADMIN 可查看全部 Session 并强制撤销任意 Session。
- 调试器增加设备会话管理窗口和管理员全局 Session 视图。
- Worker 新增 `bmaction` 管理动作和 `BMACTION` 执行确认。
- Gateway 等待 Worker 确认后才返回 `APPLIED`，3 秒超时转为 `FAILED`。
- 输入回执保存确认 Tick 和 paused 状态，重复回执保留原执行现场。
- 新增独立 Swing Desktop MultiView 客户端模块。
- 同一桌面窗口并排显示 sector-a 与 sector-b 的真实 Worker 地图。
- 桌面客户端使用 Gateway Session，支持登录、Focus、控制租约和 pause/resume/release。
- 桌面客户端提供 `--screenshot` 自渲染验收模式。
- 修复 Java HttpClient 未持久化 SameSite Cookie 的问题。
- 网页与桌面输入截止窗口扩大到 6000 Tick，适配当前高速 headless Worker。

## 本轮验证

- `bettermindustry/protocol clean build`：通过，5 个测试全部通过。
- `server:dist`：通过，包含新的 Worker `bmaction` 命令。
- `bettermindustry/orchestrator clean build`：通过，19 个测试全部通过。
- `bettermindustry/desktop-client clean build`：通过，4 个测试全部通过；三个模块共 28 个测试。
- 两个真实 Worker 完成正常 pause/resume/chat、重复回执、命令冲突、过期窗口、错误 Focus、
  无租约、跨 Client 命令 ID 和超长聊天验收。
- 重复投递没有重复执行或重复写审计。
- 两个 Worker 均优雅退出且退出码为 0，检查点存在。
- 测试端口和 Java 进程无残留；测试 Gateway 状态、审计和临时浏览器目录已清理。
- 无头 Edge 在本机以 `0xC0000005` 崩溃，因此没有自动浏览器截图；页面资源与 UI 使用的
  envelope 端点已通过实际 HTTP 请求确认。
- Edge 崩溃已定位到 Codex 沙箱的受限 `LOCALAPPDATA` 与 App-Bound Encryption 权限冲突；
  后续浏览器自动化应使用沙箱外的正常桌面浏览器。
- 完整关闭并重启 Orchestrator 后，`restart-receipt-1` 仍返回原 `APPLIED` 回执，
  审计记录保持 1 条。
- 无 Cookie 和伪造 `X-BM-Client-Id` 均返回 401。
- 同源 Session 创建成功，Cookie 为 HttpOnly；状态文件不包含明文令牌。
- 撤销 Session 后原 Cookie 返回 401，另一 Session 可立即取得原本被占用的控制权。
- Orchestrator 重启后原 Session 令牌仍能恢复同一个 Client ID，撤销后立即失效。
- 非法 Host 返回 421，其他本地端口 Origin 返回 403，同源 Origin 返回 200。
- 首个账户注册为 ADMIN，第二个账户注册为 PLAYER。
- 错误密码返回 401，账户文件不包含明文密码。
- GUEST 获取控制和管理 Worker 均返回 403。
- PLAYER 可获取控制，但管理 Worker 返回 403。
- ADMIN 可保存检查点，完整重启后两个账户仍以原角色登录。
- 两个设备 Session 显示不同 User-Agent 标签，且只有一个标记为当前设备。
- 账户撤销指定设备后旧 Cookie 返回 401。
- “退出其他设备”只保留当前 Session，其他设备立即返回 401。
- ADMIN 全局会话列表可看到 PLAYER、ADMIN 和访客，并可强制撤销目标 Session。
- 连续错误密码状态为 `401, 401, 401, 401, 429`，第 5 次返回
  `Retry-After: 300`；限流期间正确密码同样被拒绝。
- `status` 成功回执带 Worker `acknowledgementTick`。
- `pause` 回执带 `acknowledgementPaused=true`，`resume` 回执带 `false`。
- `chat` 成功等待 Worker 执行确认后返回 `APPLIED`。
- 重复 `pause` 回执保留第一次确认的 Tick 与 paused 状态。
- Worker 停止后新输入回执持久化为 `FAILED`，详情为 Worker 未运行。
- 安全测试：GUEST 输入返回 403；未知动作、非法命令 ID、151 字符聊天均返回 400。
- Chat 注入文本 `hello"; exit` 返回 APPLIED，Worker 和端口仍保持运行。
- 同一 `commandId` 改成另一动作返回 409；Worker 停止后的回执持久化为 FAILED。
- Desktop MultiView 窗口实际启动，标题为 `BetterMindustry · Desktop MultiView`。
- 自渲染截图确认同一窗口并排显示 Tendrils/Wasteland 与 Veins 两个真实地图。
- 桌面 GatewayClient 完成 GUEST→ADMIN 登录、切换 Focus、获取控制、pause/resume 和释放控制。
- 截图验收发现并修复 Cookie 未持久化与 Tick 窗口过窄两个真实问题。

## 当前架构决定

- 长期宇宙显式建模为 `Universe / Solar System / Planet / Sector`。
- 每个 Sector 默认持续完整固定 Tick，玩家在线与否只影响画面和网络订阅。
- 第一阶段由独立进程承载 Sector，避免 Mindustry 全局单例互相污染。
- 客户端目标不是只显示一个世界，而是维护多个 `WorldView`。
- 多视口 MVP 先使用主线程顺序场景绑定；长期再迁移到真正可实例化的 `GameContext`。
- 不把账本平均产率标成精确模拟。
- 上游同步采用差异审查和人工移植，不自动引入完整历史。
- 当前身份可以是匿名访客或绑定本地账户的 Session；外部身份提供商尚未接入。
- 控制面只信任同源请求、有效 Cookie 和固定 Worker 命令，不信任客户端声明的身份。
- 当前账户系统只用于本地调试；远程开放前必须增加受控管理员引导、密码恢复和账户管理。
- 登录限流目前是单 Orchestrator 内存窗口，远程多实例前需要共享限流与审计存储。
- Worker 确认超时为 3 秒；高延迟部署前需要把超时和重试策略能力化。
- 当前桌面窗口使用 Worker PNG 快照，尚未嵌入 Mindustry World/Entity/Shader 场景。
- 多视口目前固定并排、数量随 Worker 数量增长，尚未加入布局上限和性能降级。

## 下一步

M1B 后续：

1. 完成 M1B-DesktopMultiView 检查点封存和安全测试。
2. 将 PNG 多视口原型迁移到真正的 Mindustry World 场景与实体渲染。
3. 增加多视口数量上限、布局切换和性能降级。
4. 下一阶段再评估受控移动、建造和单位命令。
5. 为动作确认加入能力协商、超时配置和高延迟重试策略。
6. 将单进程登录限流迁移到多实例可共享的存储与审计。

双世界手工实验记录见 `docs/bettermindustry/spikes/0001-two-worker-manual.md`。
调试器说明见 `docs/bettermindustry/tools/debugger.md`。
诊断协议见 `docs/bettermindustry/diagnostics.md`。
Render 快照流见 `docs/bettermindustry/render-stream.md`。
M1A 验收记录见 `docs/bettermindustry/devlog/2026-10-22-m1a-acceptance.md`。

## 未决问题

- 多区块画面最终是独立视口，还是需要无缝地理拼接。
- 首个部署目标是 Windows 本地服务器还是 Linux 容器。
- 多视口 MVP 的初始上限和最低硬件目标。

## 新对话交接要求

开始改动前运行：

```powershell
git status --short --branch
git log --oneline -10
git remote -v
```

阅读 `AGENTS.md`、`docs/bettermindustry/architecture.md`、`roadmap.md` 和 `workflow.md`。阶段完成后更新本文件，不要只把结果留在聊天中。
