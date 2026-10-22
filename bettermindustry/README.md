# BetterMindustry 平台代码

本目录存放不直接属于 Mindustry 上游模块的平台代码。M0 阶段先固定职责，不提前写不可验证的业务实现。

计划结构：

```text
bettermindustry/
  orchestrator/   控制层：世界清单、进程生命周期、路由与审计
  world-worker/   世界进程启动器：隔离配置、端口、存档和健康检查
  protocol/       共享协议模型：版本、能力协商、事件信封
  runtime/        本地开发运行目录，不提交存档和日志
```

代码扩展原则：

- 控制层不依赖客户端渲染。
- 协议模块不依赖具体世界实现。
- 世界进程启动器只负责受控启动和适配，不复制 Mindustry 模拟逻辑。
- 所有模块使用独立测试，并逐步加入 Gradle。

当前独立 Gradle 模块：

- `orchestrator`：Worker 启停、Web 调试器、快照流。
- `protocol`：版本化 Render Frame。

两者与 Mindustry 核心构建分离，避免控制层修改污染上游模块。
