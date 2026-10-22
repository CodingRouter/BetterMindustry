# BetterMindustry 文档索引

这里只记录 BetterMindustry 自己的产品与工程决策。Mindustry 上游文档仍以根目录原有文件为准。

| 文档 | 内容 |
| --- | --- |
| [当前状态](status.md) | 当前里程碑、验证结果、下一步和未决问题 |
| [路线图](roadmap.md) | 从上游基线到多世界原型的阶段目标与退出条件 |
| [总体架构](architecture.md) | 客户端、控制层、世界进程、持久化和协议的关系 |
| [基线记录](baseline.md) | 固定版本、工具链和可重复构建信息 |
| [风险清单](risks.md) | GPL、兼容性、确定性和性能风险 |
| [开发流程](workflow.md) | 分支、提交、标签、验证和多对话交接规则 |
| [变更记录](changelog.md) | BetterMindustry 自己的里程碑变化 |
| [双世界手工实验](spikes/0001-two-worker-manual.md) | 两个隔离 Worker 的启动、隔离和恢复证据 |
| [Web 调试器](tools/debugger.md) | 本地双 Worker 状态、日志、启停和命令控制 |
| [开发日志](devlog/2026-10-22.md) | 2026-10-22 的工作、验证和下次恢复入口 |
| [诊断协议](diagnostics.md) | Worker 的 `BMSTATUS` 单行诊断格式 |
| [ADR-0001](decisions/0001-world-isolation.md) | 为什么第一阶段用独立进程承载每个世界 |
| [ADR-0002](decisions/0002-multi-world-client.md) | 多视口客户端为什么先采用顺序场景绑定 |
| [ADR-0003](decisions/0003-snapshot-fork.md) | 为什么在线仓库从 v160.7 源码快照独立起史 |
| [ADR-0004](decisions/0004-tick-render-separation.md) | 为什么 Game Tick 与 Render Tick 必须分离 |

术语约定：

- **世界**：一张可独立保存和推进的地图状态。
- **世界进程**：只负责一个权威世界的独立 JVM 进程。
- **控制层**：负责创建、寻址、启停、监控世界，不直接推进地图模拟。
- **跨世界事件**：由一个权威世界发出、经控制层路由到另一个世界的受控消息。
- **生产账本**：记录资源输入、输出、损耗和模拟断点的可审计数据。
