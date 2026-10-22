# 2026-10-22 M1B-AccountsRoles 检查点

## 结论

身份系统从“可撤销访客 Session”扩展到账户和角色权限。控制、输入与 Worker 管理现在有
不同的最低角色要求，账户与角色可跨 Orchestrator 重启恢复。

## 完成内容

- 新增 `Role`：`GUEST / PLAYER / ADMIN`。
- 新增 `AccountBook`，支持注册、登录、查找和持久化。
- 密码使用 16 字节随机盐和 210,000 次 PBKDF2-HMAC-SHA256。
- 首个账户为 ADMIN，后续账户为 PLAYER。
- Auth Session 持久化绑定用户名。
- Gateway 认证时实时读取账户角色，账户失效会让绑定 Session 失效。
- GUEST 可以观察；PLAYER 可控制；ADMIN 可管理 Worker。
- UI 增加登录/注册对话框、用户名/角色徽标和角色按钮禁用。
- 退出并重置会销毁当前账户 Session，回到新访客身份。

## 权限矩阵

| 操作 | GUEST | PLAYER | ADMIN |
| --- | --- | --- | --- |
| 查看状态/SSE/地图 | 是 | 是 | 是 |
| 修改自己的视口 | 是 | 是 | 是 |
| 获取控制/发送输入 | 否 | 是 | 是 |
| 启停/保存 Worker | 否 | 否 | 是 |
| 原始 Worker 管理命令 | 否 | 否 | 是 |

## 运行验收

```text
首个账户 admin_test -> ADMIN
第二个账户 player_test -> PLAYER
错误密码 -> 401
GUEST acquire -> 403
GUEST worker save -> 403
PLAYER acquire -> 200
PLAYER worker save -> 403
ADMIN worker save -> 200
账户文件明文密码 -> 不存在
账户文件 passwordHash -> 存在
完整重启后 admin_test -> ADMIN
完整重启后 player_test -> PLAYER
```

## 构建

- `bettermindustry/protocol clean build`：通过，5 个测试。
- `bettermindustry/orchestrator clean build`：通过，14 个测试。
- 两个模块共 19 个测试，全部通过。

## 检查点边界

本检查点完成账户和角色授权，不继续进入移动、建造或单位输入。下一阶段开始前应保持当前
权限模型稳定，并优先补充登录限流、设备管理和管理员撤销。

## 收工状态

- 两个 Worker 均优雅退出，退出码为 0，检查点存在。
- `19090 / 16567 / 16568` 无监听残留。
- 测试账户、Gateway 状态和审计文件在验收后清理。
