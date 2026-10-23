# BetterMindustry 变更记录

本文件记录 BetterMindustry 自己的里程碑变化，不替代上游 Mindustry 变更记录。

## Unreleased

### Added

- 多世界持续模拟与多视口客户端总体架构。
- Sector 完整精确模拟原则。
- `Universe / Solar System / Planet / Sector` 领域层级。
- 两个隔离 Server Worker 的手工验证记录。
- 本地 Web Worker 调试器和启停/保存/命令 API。
- Game Tick 与 Render Tick 分离的架构决策。
- 配置驱动 Worker 清单和真实 TCP 端口探测。
- `bmstatus` 结构化 Game Tick 诊断和面板指标。
- `bettermindustry.render.v1` 单帧和 SSE Render Tick 快照流。
- Headless 地图 PNG、单位/建筑快照和浏览器 Canvas 地图预览。
- 独立 BetterMindustry PR 模板和上游 AI Policy 适用边界说明。
- M1A 双 Worker 连续运行、故障隔离、检查点和控制层恢复验收。
- 修复 SSE 与总览并发调用导致的诊断命令交错。
- Gateway Focus/WorldView 状态和 `bettermindustry.render.v2`。
- 浏览器 FULL/SUMMARY/OFF 多视口原型。
- Gateway Focus/ViewMode 持久化和按视口模式采样节流。
- Gateway Focus 控制权租约、竞争拒绝、自动续约和过期。
- 多 Client Session、独立 Focus/ViewMode 和有效视口合并。
- Focus 控制租约接入首个授权输入端点。
- 受控输入 v1、pause/resume/chat 和 Gateway 输入审计。
- 版本化 Input Envelope、命令 ID、Tick 截止窗口和重复投递回执。
- 同一命令 ID 的客户端/命令冲突检测，以及只执行一次的输入审计。
- 调试器 pause/resume/chat 切换到 Input Envelope，并增加同信封网络重试。
- Protocol 模块加入输入信封单元测试。
- 输入回执持久化到 Gateway 状态文件，并采用临时文件原子替换。
- Orchestrator 重启后继续返回已完成命令的原回执。
- 启动时将无法确认完成的 `PENDING` 回执恢复为 `FAILED`，防止重复执行。
- Orchestrator 模块加入回执账本持久化、冲突、过期和容量测试。
- 记录 Codex Windows 沙箱内无头 Edge 崩溃的排查结论和外部浏览器验证方式。
- 服务端签发 256 位随机令牌的认证访客 Session，Client ID 不再由浏览器声明。
- Session Cookie 使用 HttpOnly 和 SameSite=Strict，状态文件只保存令牌 SHA-256 哈希。
- 除首页和 Session 端点外，HTTP API、SSE 和地图资源全部要求有效 Session。
- Session 可跨 Orchestrator 重启恢复，撤销时立即释放其控制租约。
- 本地控制面增加 Host 校验防 DNS rebinding，并校验写请求 Origin。
- 调试器增加“撤销会话”入口和 Auth Session 自动建立流程。
- Orchestrator 模块加入 Session 签发、认证、撤销、持久化、过期和容量测试。
- 新增 GUEST/PLAYER/ADMIN 账户与角色授权。
- 密码使用随机盐和 210,000 次 PBKDF2-HMAC-SHA256 派生哈希保存。
- Session 绑定用户名，账户跨 Orchestrator 重启恢复。
- PLAYER 才能获取控制与发送输入，ADMIN 才能管理 Worker。
- 调试器增加登录/注册对话框、身份徽标和角色按钮禁用。
- Orchestrator 模块加入账户注册、密码验证、持久化和角色测试。
- 登录失败按地址和用户名实施 5 次/5 分钟限流，超限返回 429 与 `Retry-After`。
- Session 保存设备 User-Agent 标签，账户可列出并撤销自己的设备 Session。
- 支持“退出其他设备”和管理员强制撤销任意 Session。
- 调试器增加设备会话管理窗口和管理员全局 Session 视图。
- Orchestrator 增加登录限流测试，并扩展 Session 标签与账户绑定测试。
- Worker 新增带 `commandId` 的 `bmaction` 管理动作和 `BMACTION` 返回确认。
- Gateway 等到 Worker 确认后才将输入回执标记为 `APPLIED`，3 秒无确认则失败。
- 输入回执持久化 Worker 确认 Tick 与 paused 状态，重复投递保留原执行现场。
- ServerControl 限制确认动作仅能使用 `status/pause/resume/chat`。
- 新增独立 Swing Desktop MultiView 客户端模块。
- 桌面窗口通过认证 Gateway Session 并排显示两个真实 Sector 地图。
- 桌面客户端支持 Focus、控制租约、pause/resume/release 和角色按钮状态。
- 增加 `--screenshot` 自渲染验收模式，并修复 Java HttpClient 的 SameSite Cookie 持久化。
- 调试器输入 Tick 窗口从 120 扩展到 6000，适配当前高速 headless Worker。

### Next

- M1：自动管理双 Sector Worker 的 Orchestrator CLI。

## M0 - 2026-10-22

### Added

- 基于 Mindustry `v160.7` 的本地项目基线。
- 项目入口、架构文档、路线图、风险清单和 ADR。
- 根目录与项目级协作规则。
- 开发、版本管理和多对话交接流程。

### Verified

- `tests:test`：335 个测试通过。
- `server:dist`：构建成功。
- `desktop:dist`：构建成功。
- 服务端启动与正常退出验证成功。
