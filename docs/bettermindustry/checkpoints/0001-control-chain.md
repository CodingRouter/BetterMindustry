# M1B-ControlChain 检查点

- 建立日期：2026-10-22
- 状态：已完成（2026-10-22）

## 范围

只实现受控命令协议 v1：

- `status`
- `pause`
- `resume`
- `chat`

不实现移动、建造、单位命令或跨 Sector 传送。

## 必须满足

- 只有 Focus Sector 可以接收输入。
- 请求必须带有效 Client ID。
- Client 必须持有该 Sector 的有效控制租约。
- 观察者、无租约、过期租约和错误 Focus 返回 403。
- 聊天内容去除换行并限制长度。
- 成功输入写入 Gateway 审计记录。
- 浏览器不再通过原始 Worker 命令接口发送游戏输入。
- UI 提供暂停、继续和聊天入口，并显示授权错误。
- Protocol/Orchestrator 构建通过。
- 更新状态文档、Gateway 文档、changelog 和 devlog。

## 停止条件

上述检查全部完成后停止新增功能，执行一次完整验收并创建 `2026-10-22-control-chain-checkpoint.md` 开发日志。

下一阶段必须在完成检查点之后才能开始。
