# 本地 Web 调试器

调试器用于观察和操作两个真实 Mindustry Server Worker，不依赖游戏图形窗口。

## 启动

```powershell
.\gradlew.bat -p bettermindustry/orchestrator run
```

打开：

```text
http://127.0.0.1:19090/
```

调试器只监听 `127.0.0.1`，不会暴露到局域网。

## Worker 配置

测试 Worker 清单位于：

```text
bettermindustry/orchestrator/worlds.properties
```

格式：

```properties
workers=sector-a,sector-b

sector-a.map=wasteland
sector-a.port=16567
sector-a.runtimeDir=bettermindustry/runtime/debugger/sector-a

sector-b.map=veins
sector-b.port=16568
sector-b.runtimeDir=bettermindustry/runtime/debugger/sector-b
```

启动时会校验 Worker ID、端口、运行目录、必需字段和重复值。运行目录必须位于项目目录内。

只验证配置：

```powershell
.\gradlew.bat -p bettermindustry/orchestrator run --args="--validate-config"
```

## 显示内容

- Worker ID、地图和端口。
- 进程状态、PID、运行时长和退出码。
- 实际 TCP 端口监听状态。
- `BMSTATUS` 结构化诊断：Game Tick、游戏时间、波次、暂停、玩家、单位、敌人和建筑数。
- 真实地图缩略图、单位和建筑快照。
- 独立检查点是否存在。
- 服务端实时日志。
- Game Tick 与计划中的 Render Tick 管线。

## 控制

- 首次打开自动建立 7 天有效的访客 Session；令牌只放在 HttpOnly Cookie。
- 访客只能观察；顶部“登录 / 注册”可切换到 PLAYER 或 ADMIN。
- 第一个注册账户会成为本地 ADMIN，后续注册账户为 PLAYER。
- `撤销会话`：销毁当前身份、关闭快照流并释放该 Session 持有的控制租约。
- `启动`：启动 Worker；存在检查点时自动加载检查点，否则启动对应地图。
- `停止`：保存 `debugger-checkpoint`，然后发送 `exit`。
- `保存检查点`：发送 `save debugger-checkpoint`。
- `暂停` / `继续`：通过 Gateway 输入信封控制 Focus Worker。
- `发送聊天`：通过 Gateway 输入信封发送受控聊天。
- `启动全部` / `停止全部`：批量控制。

## 浏览器自动化限制

Codex 的 Windows 沙箱会把 `LOCALAPPDATA` 重定向到隔离目录。Edge 155 在该环境中初始化
App-Bound Encryption 时报告 `0x80070005 Access denied`，随后以 `0xC0000005` 退出，
连 `msedge --version` 都会发生，因此这不是调试器页面导致的崩溃。

`--no-sandbox` 只关闭 Chromium 内部沙箱，不能解除 Codex 外层文件与凭据限制。
需要自动截图时，应从沙箱外的正常桌面会话启动 Edge、Firefox 或专用 Playwright 浏览器。

调试器会校验 HTTP `Host` 和写请求的 `Origin`。访问时使用
`http://127.0.0.1:19090/` 或 `http://localhost:19090/`；其他 Host 会收到 HTTP 421。

## 当前 Worker

| ID | 地图 | 端口 |
| --- | --- | ---: |
| `sector-a` | `wasteland` | 16567 |
| `sector-b` | `veins` | 16568 |

运行目录：

```text
bettermindustry/runtime/debugger/
```

这些目录不进入 Git。

## 当前限制

- 调试器只观察 Game Tick Worker，不渲染地图。
- 沙箱内不能可靠启动 Edge 自动化；该限制不属于调试器应用本身。
- 支持匿名访客和本地账户；OAuth、多因素认证、密码重置和账户管理 UI 尚未实现。
- Worker 配置目前使用 Java Properties 格式，尚未加入热重载和面板内增删。
- 端口探测只确认 TCP 可连接，尚未执行应用层握手。
- 诊断通过服务端控制命令请求，尚未改成常驻推送通道。
- 地图缩略图使用 headless 调色板，尚未使用完整客户端贴图。
- 浏览器 Canvas 当前先绘制地图，单位和建筑覆盖层仍在下一阶段。
- 生产吞吐、瓶颈和资源变化趋势尚未加入。
- 渲染进程、场景订阅和多视口尚未接入。
