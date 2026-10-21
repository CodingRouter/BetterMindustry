# 当前状态与交接

- 最后更新：2026-10-22
- 当前分支：`main`
- 当前阶段：M1A 第 1 步双世界手工实验已通过
- 当前里程碑：M0 上游基线已完成

## 已完成

- 基于 Mindustry `v160.7` 建立本地分支。
- 上游提交：`17012d61d9e4c3cbd9820d87e81c24495486ac53`。
- 在线主干采用 v160.7 源码快照根提交，完整历史保存在本地归档分支。
- 335 个上游测试全部通过。
- `server:dist`、`desktop:dist` 构建成功。
- 服务端 JAR 已实际启动并正常退出。
- 项目入口、路线图、总体架构、风险、ADR 和开发流程已建立。

## 当前架构决定

- 长期宇宙显式建模为 `Universe / Solar System / Planet / Sector`。
- 每个 Sector 默认持续完整固定 Tick，玩家在线与否只影响画面和网络订阅。
- 第一阶段由独立进程承载 Sector，避免 Mindustry 全局单例互相污染。
- 客户端目标不是只显示一个世界，而是维护多个 `WorldView`。
- 多视口 MVP 先使用主线程顺序场景绑定；长期再迁移到真正可实例化的 `GameContext`。
- 不把账本平均产率标成精确模拟。
- 上游同步采用差异审查和人工移植，不自动引入完整历史。

## 下一步

M1：精确 Sector Actor 原型。

1. 定义 `SectorId`、Sector 状态机和最小宇宙清单。
2. 实现最小 Orchestrator CLI，自动启动两个独立 Server Worker。
3. 为每个 Worker 分配端口、配置、存档、日志和游戏时钟。
4. 增加可靠的进程、端口和日志健康检查。
5. 验证优雅停止、检查点保存和恢复。

双世界手工实验记录见 `docs/bettermindustry/spikes/0001-two-worker-manual.md`。

## 未决问题

- 在线仓库账号、组织、仓库名和可见性。
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
