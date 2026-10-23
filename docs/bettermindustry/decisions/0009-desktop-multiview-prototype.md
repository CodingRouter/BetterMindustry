# ADR-0009：先做 Swing 多 Sector 桌面壳体

- 状态：已接受，原型已实现
- 日期：2026-10-23

## 背景

浏览器调试器已经能看到两个 Sector，但 M1B 需要真正的桌面客户端。直接重构 Mindustry
大量 `Vars` 全局状态风险过高；需要先验证桌面窗口、会话、订阅、Focus 和输入控制。

## 决策

新增独立 Swing Desktop MultiView：

- 消费 Gateway Render Snapshot。
- 并排显示 Worker 生成的 PNG 地图。
- 复用同一账户、Session 和控制租约。
- 支持 Focus、pause、resume、release。
- 提供应用自渲染截图，避免依赖浏览器自动化。

这不替代最终 Mindustry 客户端。它用于先稳定桌面客户端与 Gateway 的边界。

## 下一阶段

在保留该壳体的前提下，逐步替换 PNG：

1. 引入 Tile/建筑/单位增量。
2. 在桌面进程中建立可切换的 Mindustry World 绑定。
3. 按视口排队网络包并顺序应用。
4. 为多个视口加入硬件预算和降级。

## 后果

优点：

- 快速得到真实桌面窗口和多图视觉证据。
- 不触碰 Mindustry 全局单例即可验证控制链。
- GatewayClient 和 UI 壳体可继续复用。

代价：

- PNG 不能显示完整实体、动画、粒子和 Shader。
- 当前多视口没有真正绑定 Mindustry 场景。
- 还需要一次更深的客户端架构迁移。
