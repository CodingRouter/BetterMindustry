# M1B-SessionSecurity 检查点

- 状态：已完成
- 日期：2026-10-22

## 目标

在进入移动、建造和更复杂的多人控制前，补齐本地账户 Session 的防爆破、设备可见性和
撤销能力。

## 退出条件

- [x] 登录失败按地址和用户名限制为 5 次/5 分钟。
- [x] 超限登录返回 HTTP 429 和 `Retry-After`。
- [x] 成功登录清除对应失败窗口。
- [x] Session 保存经过清理的设备标签。
- [x] 账户可以列出自己的全部有效 Session。
- [x] 账户可以撤销指定设备或退出其他全部设备。
- [x] ADMIN 可以列出全部 Session 并强制撤销。
- [x] 撤销 Session 会清除对应控制租约。
- [x] 调试器提供账户和管理员 Session 管理 UI。
- [x] Protocol 与 Orchestrator 测试通过。
- [x] 两个真实 Worker 验收后优雅退出。
- [x] 文档、ADR、状态和开发日志完成。

## 验证摘要

```text
Player Device A / Device B 标签 -> 正确显示
当前设备数量 -> 1
撤销 Device B -> 旧 Cookie 401
退出其他设备 -> 仅保留当前 Session
ADMIN 全局列表 -> 可见 PLAYER、ADMIN 和访客
ADMIN 强制撤销 -> 目标 Cookie 401
连续错误密码 -> 401,401,401,401,429
Retry-After -> 300
限流期间正确密码 -> 429
```

## 暂不包含

- 多实例共享限流。
- Session 地理/IP 详情与异常登录提醒。
- 密码重置、账户禁用和角色管理 UI。
- OAuth、多因素认证和可信设备。
