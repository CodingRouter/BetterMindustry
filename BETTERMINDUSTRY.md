# BetterMindustry

BetterMindustry 是基于 Mindustry 的多人游戏平台分支：在兼容原版玩法的基础上，提供可同时运行、彼此联通、持续模拟的多个持久世界。

**M0：上游基线已完成。** 开发基线是 Mindustry `v160.7`，本地稳定主干为 `main`；335 个测试通过，服务端和桌面端产物均已成功构建。

## 核心方向

1. 修复影响联机稳定性、存档可靠性和模拟一致性的关键服务端问题。
2. 通过独立世界进程实现多地图同时运行和故障隔离。
3. 建立跨世界路由、事件与物流能力，但不把多个地图硬塞进同一全局状态。
4. 在固定 Tick 和确定性前提下，为离线世界提供可复现的持续模拟，并用审计账本解释产出与瓶颈。
5. 在性能基线稳定后加入二维动态光照、阴影、环境光遮蔽和分级画质。

## 第一阶段完成标准

同一个控制服务能够启动两个独立 Mindustry 世界进程，为它们分配不同端口和存档目录，查看运行状态，并分别加入两个世界。任一世界崩溃不能带走另一个世界。

## 文档

- [当前状态与交接](docs/bettermindustry/status.md)
- [路线图](docs/bettermindustry/roadmap.md)
- [总体架构](docs/bettermindustry/architecture.md)
- [基线记录](docs/bettermindustry/baseline.md)
- [风险清单](docs/bettermindustry/risks.md)
- [开发与版本管理](docs/bettermindustry/workflow.md)
- [变更记录](docs/bettermindustry/changelog.md)
- [世界隔离决策](docs/bettermindustry/decisions/0001-world-isolation.md)
- [多视口客户端决策](docs/bettermindustry/decisions/0002-multi-world-client.md)
- [快照分叉决策](docs/bettermindustry/decisions/0003-snapshot-fork.md)
- [平台代码目录](bettermindustry/README.md)

## 构建

上游当前使用 JDK 17 编译：

```powershell
.\gradlew.bat tests:test
.\gradlew.bat server:dist
.\gradlew.bat desktop:dist
```

产物位置：

- `server/build/libs/server-release.jar`
- `desktop/build/libs/Mindustry.jar`

桌面客户端提示使用 JDK 25；服务端基线已验证可在 JDK 17 下启动。

## 上游来源

本分支以 `Anuken/Mindustry` 的 `v160.7` 源码树作为独立初始提交，遵循 GPL-3.0。在线仓库不携带上游完整 Git 历史，以保持仓库轻量；上游提交号、原始 README、许可证和版权声明继续保留，后续版本通过源码差异和人工审查同步。
