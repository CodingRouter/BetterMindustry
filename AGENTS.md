# BetterMindustry 项目规则

## 项目定位

BetterMindustry 是基于 Mindustry 的独立分支，目标是构建可长期运行、多世界持续模拟的联机平台。玩家看到的是新客户端，工程上同时包含权威服务端、世界进程管理器和新的通信协议。

第一阶段不把多个地图塞进同一 JVM。每个世界使用独立进程，保证故障隔离和可独立持久化；跨地图玩法由控制层协调。

## 上游基线

- 上游：`Anuken/Mindustry`
- 基线：`v160.7`
- 提交：`17012d61d9e4c3cbd9820d87e81c24495486ac53`
- 本地稳定主干：`main`
- 许可证：GPL-3.0

BetterMindustry 的在线仓库使用独立根提交，从 Mindustry `v160.7` 的源码快照开始，不携带 Mindustry 的完整 Git 祖先历史。完整历史仅保存在本地归档分支，用于追溯和人工比较。

远端约定：

- `upstream`：`Anuken/Mindustry`，只用于同步原版。
- `origin`：BetterMindustry 的在线仓库；创建后仅向这里推送本项目分支。

未经用户要求，不强制推送、不改写历史。发布里程碑使用 `m0-*`、`m1-*` 等标签。

## 构建与运行环境

- 上游 README、`settings.gradle` 和 `build.gradle` 均要求使用 JDK 17 编译。
- 最新桌面客户端提示用户使用 JDK 25，源码注释说明当前实际最低版本仍是 17。
- 编译、测试和构建使用 JDK 17；验证桌面运行时可使用本机 JDK 25。
- Windows 构建：`.\gradlew.bat server:dist`
- Windows 测试：`.\gradlew.bat tests:test`
- Windows 客户端构建：`.\gradlew.bat desktop:dist`

## 工程约束

- 先确认原版行为，再修改核心代码；一次只解决一个可验证的问题。
- 世界模拟必须保持单世界内的确定性顺序，不用“随机分配线程”决定 Tick 顺序。
- 控制层不直接修改世界内部状态，只通过受控命令和事件与世界进程通信。
- 无玩家 Sector 默认继续完整固定 Tick；只有明确暂停、结束或资源保护时才停止模拟。
- 不允许在未告知玩家或开发者的前提下，把精确模拟替换成账本级估算。
- 多视口原型可以使用顺序场景绑定，但所有网络包必须携带 `sectorId` 并在主线程绑定后应用。
- 同步 Mindustry 时比较源码树差异并人工移植，不把完整上游历史并入在线主分支。
- 新协议必须带版本号、能力协商和明确的兼容边界。
- 默认保留原版客户端兼容路径；新玩法使用独立能力标记。
- 核心修改必须补测试；无法自动测试的行为要写清最小复现和人工验证步骤。

## 文档与目录

- 项目入口：根目录 `BETTERMINDUSTRY.md`
- 设计文档：`docs/bettermindustry/`
- 当前进度与交接：`docs/bettermindustry/status.md`
- 分支、提交和验证规则：`docs/bettermindustry/workflow.md`
- 里程碑变更：`docs/bettermindustry/changelog.md`
- 新平台代码：`bettermindustry/`
- 临时排查资料：`temp/`

不覆盖上游 `README.md`。项目概况、决策和路线单独维护。

## 分支与提交

- `main`：可持续构建的稳定主干。
- `feat/<slug>`：新功能。
- `fix/<slug>`：缺陷修复。
- `spike/<slug>`：有时间盒和退出条件的实验。
- `docs/<slug>`：文档和协作规则。

提交信息使用 Conventional Commits 风格，例如：

```text
feat(worker): add sector process supervisor
fix(net): route snapshots by sector id
docs(arch): define multi-world render subscription
```

核心功能和协议改动通过短分支完成，验证后合入 `main`。不提交 `build/`、`runtime/`、存档、日志、密钥和大型生成物。

## 新对话启动顺序

1. 阅读根目录 `AGENTS.md`。
2. 阅读 `BETTERMINDUSTRY.md`、`docs/bettermindustry/status.md` 和 `docs/bettermindustry/workflow.md`。
3. 查看 `git status --short --branch`、`git log --oneline -10` 和远端。
4. 确认当前里程碑与未决问题后再修改代码。

## 交付

完成改动后实际构建或测试，并报告通过项、未验证项和剩余风险。若修改了上游文件，列出每个文件和修改理由。阶段完成后同步更新 `status.md`、`changelog.md` 和相关 ADR。
