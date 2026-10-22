# 当前状态与交接

- 最后更新：2026-10-22
- 当前分支：`feat/m1b-gateway`
- 当前阶段：M1B Gateway 持久化、采样节流和 Focus 控制租约已完成
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

## 当前架构决定

- 长期宇宙显式建模为 `Universe / Solar System / Planet / Sector`。
- 每个 Sector 默认持续完整固定 Tick，玩家在线与否只影响画面和网络订阅。
- 第一阶段由独立进程承载 Sector，避免 Mindustry 全局单例互相污染。
- 客户端目标不是只显示一个世界，而是维护多个 `WorldView`。
- 多视口 MVP 先使用主线程顺序场景绑定；长期再迁移到真正可实例化的 `GameContext`。
- 不把账本平均产率标成精确模拟。
- 上游同步采用差异审查和人工移植，不自动引入完整历史。

## 下一步

M1B 后续：

1. 将 Focus 控制租约路由到真实玩家输入。
2. 增加用户认证和控制权撤销。
3. 按客户端带宽动态调整订阅频率。
4. 将浏览器 WorldView 迁移到真正的 Mindustry 桌面客户端。

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
