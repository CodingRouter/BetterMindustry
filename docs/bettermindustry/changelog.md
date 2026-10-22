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
