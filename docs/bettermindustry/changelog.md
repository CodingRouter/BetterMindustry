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
