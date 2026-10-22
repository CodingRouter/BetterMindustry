# 2026-10-22 M1B-ControlChain 检查点日志

## 结论

受控输入权限链 v1 已完成。Gateway 不再只是保存 Focus 和 ViewMode，而是已经实际决定一条游戏侧控制命令是否可以进入 World Worker。

## 完成内容

- Client Session 独立维护 Focus 和 ViewMode。
- Gateway 合并所有 Session 的视口需求，使用 `FULL > SUMMARY > OFF`。
- 控制租约按 Sector 独立管理。
- 受控命令 v1：`status / pause / resume / chat`。
- 输入必须同时满足有效 Client ID、正确 Focus、有效控制租约。
- 聊天内容清理控制字符和换行，最多 150 字符。
- 成功输入写入内存审计和 `gateway-audit.log`。
- 普通 UI 改为授权聊天输入，不再直接暴露原始 Worker 命令框。
- 浏览器增加暂停、继续、获取/释放控制按钮。
- Render v2 携带 `clientId`、Focus、控制者和 Session ViewMode。

## 验收记录

无租约聊天：

```text
POST input/chat/sector-a -> 403
```

持有租约：

```text
acquire sector-a -> 200
pause sector-a -> 200
diagnostics paused=true
resume sector-a -> 200
diagnostics paused=false
chat "hello\nstatus" -> 200
```

聊天内容最终记录为 `hello status`。

拒绝规则：

```text
错误 Focus 输入 -> 403
观察者输入 -> 403
无租约输入 -> 403
过期租约输入 -> 403
超长聊天 151 字符 -> 400
```

多 Client：

```text
client-a 控制 sector-a -> 200
client-b 控制 sector-b -> 200
client-b 抢 sector-a -> 409
```

浏览器验证：

- 暂停、继续、聊天按钮存在。
- 原始命令按钮不存在。
- Client ID 稳定存储在 localStorage。
- 地图加载和 WorldView 状态正常。

构建：

```text
bettermindustry/protocol build: 通过
bettermindustry/orchestrator build: 通过
```

## 收工状态

- 两个 Worker 均优雅停止，退出码 0，检查点存在。
- 调试器和无头 Edge 验证会话关闭。
- 无 Java、Gradle 或端口残留。
- Gateway 测试状态文件已清理，下次启动从默认 Session 开始。

## 下一阶段

- 设计正式输入信封和幂等规则。
- 增加用户认证和控制权撤销。
- 再接入移动、建造和真正的 Mindustry 桌面多 WorldView 客户端。
