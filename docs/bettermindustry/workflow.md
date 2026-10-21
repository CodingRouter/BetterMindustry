# 开发与版本管理

## 目标

BetterMindustry 会进行高频实验和多对话协作，因此仓库必须满足三个条件：

- 任何稳定提交都能说明它解决了什么问题。
- 新对话能靠文档恢复上下文，不依赖聊天历史。
- 失败实验可以独立保存或丢弃，不污染稳定主干。

## 远端

- `origin`：BetterMindustry 的在线仓库。
- `upstream`：`https://github.com/Anuken/Mindustry.git`。

同步上游时只从 `upstream` 获取，不向 `upstream` 推送。本地已把 `upstream` 的 push URL 设为 `DISABLED`，防止误操作。`origin` 创建后再绑定并验证推送权限。

### 上游同步策略

BetterMindustry 在线主干使用独立根提交，不包含 Mindustry 完整历史。同步上游时：

1. 从 `upstream` 获取目标版本。
2. 用 `git diff` 或独立工作区比较源码树。
3. 只移植经过审查的修复、安全更新和兼容性改动。
4. 明确记录来源版本和上游提交号。
5. 不直接执行会引入完整上游祖先历史的普通合并。

完整历史只保留在本地 `archive/mindustry-full-history` 归档分支，用于追溯和差异研究，不推送到公开仓库。

## 分支

| 分支 | 用途 |
| --- | --- |
| `main` | 可持续构建的稳定主干 |
| `feat/<slug>` | 新功能 |
| `fix/<slug>` | 缺陷修复 |
| `spike/<slug>` | 有时间盒和退出条件的技术实验 |
| `docs/<slug>` | 文档、规则和流程 |

分支尽量短命。一个分支只服务一个阶段目标，不把无关实验混在一起。

## 提交

使用 Conventional Commits：

```text
feat(worker): add sector process supervisor
fix(net): route snapshots by sector id
refactor(core): isolate active world binding
test(worker): verify checkpoint recovery
docs(arch): define exact offscreen simulation
build(gradle): add runtime api module
ci(actions): cache gradle dependencies
chore(repo): ignore local runtime data
```

提交要求：

- 标题说明结果，不使用“update files”“修一下”“进度”这类空标题。
- 一个提交解决一个可以解释的问题。
- 正文优先说明“为什么”，必要时记录关键权衡和验证命令。
- 不提交构建产物、运行目录、存档、日志、密钥、令牌或大型生成文件。
- 不修改已经共享的历史；不使用强制推送覆盖 `main`。

## 验证与合并

提交前至少执行与改动相关的验证：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME = "$PWD\bettermindustry\runtime\gradle-user-home"
.\gradlew.bat tests:test
```

涉及客户端或服务端产物时再执行：

```powershell
.\gradlew.bat server:dist
.\gradlew.bat desktop:dist
```

网络需要代理时通过环境变量或 JVM 参数临时配置，不把个人代理地址提交进仓库。

合并前检查：

- `git status --short` 中没有意外文件。
- 测试通过，或明确记录未验证项。
- 对应文档、状态和 ADR 已更新。
- 没有把近似结果标成精确结果，也没有把推测写成已实现。

## 标签与发布

里程碑标签使用稳定、可排序的名称：

```text
m0-baseline
m1-workers
m2-multiview
```

标签只在里程碑满足退出条件时创建。GitHub Release、二进制包和更新时间另行记录，不将大型 JAR 提交进 Git 历史。

## 多对话交接

每个新对话先读：

1. 根目录 `AGENTS.md`。
2. `BETTERMINDUSTRY.md`。
3. `docs/bettermindustry/status.md`。
4. 当前阶段对应的路线图和 ADR。

每个阶段结束前更新 `status.md` 和 `changelog.md`。状态文档写明：

- 当前目标。
- 已完成内容。
- 已执行验证。
- 下一步。
- 阻塞与未决决策。

## 安全检查

- 不把 GitHub、云服务或服务器凭据写入仓库。
- 不直接执行来源不明的脚本。
- 不覆盖用户未提交的改动。
- 不删除运行中的世界、存档或数据库；迁移和清理前先备份。
- 上线前审查 GPL-3.0 分发义务、上游名称使用和第三方素材许可。
