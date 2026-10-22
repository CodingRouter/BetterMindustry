# M1B-AccountsRoles 检查点

- 状态：已完成
- 日期：2026-10-22

## 目标

在继续输入协议和世界玩法前，先把可撤销 Session 升级成有账户、密码和角色边界的身份系统。

## 退出条件

- [x] 第一个账户注册为 ADMIN，后续账户注册为 PLAYER。
- [x] 密码使用随机盐和 PBKDF2-HMAC-SHA256，不保存明文。
- [x] 正确密码可以登录，错误用户名或密码返回 401。
- [x] Session 绑定账户名，并从账户数据读取当前角色。
- [x] GUEST 可以观察，但不能取得控制或管理 Worker。
- [x] PLAYER 可以取得控制和发送受控输入，但不能管理 Worker。
- [x] ADMIN 可以管理 Worker。
- [x] 账户和角色跨 Orchestrator 重启恢复。
- [x] 调试器具备登录、注册、角色显示和按钮禁用。
- [x] Protocol 与 Orchestrator 测试通过。
- [x] 两个真实 Worker 优雅退出且检查点存在。
- [x] 文档、ADR、状态和开发日志完成。

## 验证摘要

```text
首个账户 -> ADMIN
第二个账户 -> PLAYER
错误密码 -> 401
GUEST acquire/save -> 403 / 403
PLAYER acquire/save -> 200 / 403
ADMIN save -> 200
账户文件包含明文密码 -> 否
Orchestrator 重启后重新登录 -> ADMIN / PLAYER 均恢复
```

## 暂不包含

- 登录失败限流。
- 密码重置。
- 账户禁用和角色修改 UI。
- Session/设备列表和踢出其他设备。
- OAuth、邮箱或多因素认证。

这些能力必须在开放远程访问前进入下一安全阶段。
