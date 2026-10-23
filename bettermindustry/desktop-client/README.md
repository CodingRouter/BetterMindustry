# BetterMindustry Desktop MultiView

Swing 桌面多 Sector 原型。它通过 Gateway 建立认证 Session，并排显示两个真实 Worker
地图快照，支持 Focus 切换、控制租约以及 pause/resume。

启动 Orchestrator 后运行：

```powershell
.\gradlew.bat -p bettermindustry/desktop-client run
```

默认连接：

```text
http://127.0.0.1:19090
```

当前地图内容是 Worker 生成的真实 PNG 快照，不是集成后的 Mindustry 全量场景渲染。

可自渲染一张窗口截图用于验收：

```powershell
.\gradlew.bat -p bettermindustry/desktop-client run --args="--screenshot temp/desktop-multiview.png"
```
