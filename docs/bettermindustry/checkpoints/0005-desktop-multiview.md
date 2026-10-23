# M1B-DesktopMultiView 检查点

- 状态：已完成
- 日期：2026-10-23

## 目标

把多世界从浏览器调试器推进到独立桌面窗口，在同一个窗口中同时观察两个真实 Sector，
并验证 Focus 与控制链。

## 退出条件

- [x] 新建独立 `desktop-client` Gradle 模块。
- [x] Swing 窗口通过 Gateway Session 认证。
- [x] 同一窗口并排显示两个真实 Worker 地图。
- [x] 显示 Tick、游戏时间、暂停、玩家、单位、敌人、建筑和视口模式。
- [x] 支持登录、注册、Focus 切换、控制租约、pause/resume/release。
- [x] GUEST/PLAYER/ADMIN 按钮状态与权限一致。
- [x] 提供自渲染 `--screenshot` 验收模式。
- [x] 截图确认两个真实地图同时可见。
- [x] Desktop GatewayClient 完成登录、Focus、控制和输入端到端验收。
- [x] 修复 Cookie 未持久化和 Tick 截止窗口过窄。
- [x] 两个 Worker 优雅退出，文档与开发日志完成。

## 当前边界

桌面窗口消费 Gateway 的 PNG 地图快照，尚未嵌入 Mindustry `World`、实体组和 Shader。
因此该检查点证明的是桌面多订阅和 Focus 控制边界，不是最终无缝游戏场景。
