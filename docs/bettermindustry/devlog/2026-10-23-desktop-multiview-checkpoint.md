# 2026-10-23 M1B-DesktopMultiView 检查点

## 结论

多世界第一次进入独立桌面窗口：同一窗口并排显示两个真实 Sector 地图，并能通过 Gateway
登录、切换 Focus、取得控制和执行 pause/resume。

## 完成内容

- 新增 `bettermindustry/desktop-client` Gradle 模块。
- 使用 Java Swing 构建 1480×900 桌面窗口。
- 通过 HttpOnly Session Cookie 访问 Gateway。
- 并排显示 `sector-a` 与 `sector-b` 的真实 Worker PNG。
- 显示 Tick、游戏时间、单位、敌人、建筑、暂停和视口模式。
- 增加登录、注册、重新连接访客。
- 增加 Focus、获取控制、释放控制、暂停和继续。
- 增加 `--screenshot` 自渲染验收模式。

## 实际验收

```text
窗口标题 -> BetterMindustry · Desktop MultiView
sector-a -> Tendrils/Wasteland 地图，FOCUS
sector-b -> Veins 地图，OBSERVE
两张地图 -> 同一窗口同时可见
Desktop GatewayClient:
  GUEST Session -> ADMIN Session
  Focus sector-b -> 成功
  acquire sector-b -> owner 当前 Session
  pause -> APPLIED, ackPaused=true
  resume -> APPLIED, ackPaused=false
  release -> owner=null
```

## 发现并修复

- Java HttpClient 没有自动保存 `SameSite=Strict` Cookie，改为显式捕获并发送
  `bm_session`。
- headless Worker 的 Tick 推进远快于现实时间，客户端截止窗口从 120/600 Tick 调整为
  6000 Tick。
- Swing `JViewport` 白色背景导致指标不可读，补齐暗色主题。

## 构建

- `bettermindustry/desktop-client clean build`：通过，4 个测试。
- Protocol、Orchestrator、Desktop Client 共 28 个测试全部通过。

## 收工状态

- 两个 Worker 均优雅退出，退出码为 0，检查点存在。
- 桌面窗口和 Orchestrator 已退出。
- `19090 / 16567 / 16568` 无监听残留。
- 自渲染截图保存在 `bettermindustry/desktop-client/temp/`，不进入 Git。
