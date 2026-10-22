# 2026-10-22 M1B-SessionSecurity 检查点

## 结论

账户 Session 从“可撤销 Cookie”升级为具备登录防爆破、设备列表、自助撤销和管理员强制撤销
的安全边界。

## 完成内容

- 新增 `LoginThrottle` 滑动窗口限流。
- 第 5 次错误密码开始返回 HTTP 429 和 `Retry-After`。
- 登录成功清除对应失败窗口。
- Session 保存清理后的 User-Agent 设备标签。
- 账户可以列出自己的 Session。
- 账户可以撤销指定设备或退出其他全部设备。
- ADMIN 可以列出全部账户/访客 Session 并强制撤销。
- 调试器增加“会话”窗口和管理员全局视图。
- 返回给客户端的 Session 数据不包含令牌或令牌哈希。

## 运行验收

```text
Player Device A / Device B -> 标签正确，当前设备 1 个
撤销 Device B -> 旧 Cookie 401
退出其他设备 -> revoked=1，Device C Cookie 401
ADMIN 全局列表 -> player_test / admin_test 可见
ADMIN 强制撤销 Player A -> Cookie 401
错误密码序列 -> 401,401,401,401,429
Retry-After -> 300
限流期间正确密码 -> 429
```

## 构建

- `bettermindustry/protocol clean build`：通过。
- `bettermindustry/orchestrator clean build`：通过，16 个测试。
- Protocol 与 Orchestrator 共 21 个测试全部通过。

## 收工状态

- 两个 Worker 均优雅退出，退出码为 0，检查点存在。
- `19090 / 16567 / 16568` 无监听残留。
- 测试账户、Gateway 状态与审计文件在验收后清理。

## 检查点位置

本检查点完成后暂停扩展玩法功能。受控移动、建造和单位输入应在下一阶段重新评估，并继续
依赖当前账户、角色和 Session 授权链。
