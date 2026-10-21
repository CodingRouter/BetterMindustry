# 上游基线记录

## 版本

| 项目 | 值 |
| --- | --- |
| 上游仓库 | `https://github.com/Anuken/Mindustry` |
| GitHub Release | `v8 Build 160.7` |
| Git 标签 | `v160.7` |
| 提交 | `17012d61d9e4c3cbd9820d87e81c24495486ac53` |
| 发布日期 | 2026-10-08 |
| 本地分支 | `main` |
| 许可证 | GNU GPL v3 |

本地仓库把上游保存为 `upstream` 远端，并采用 `--depth 1 --single-branch` 浅克隆。需要回溯历史时再显式补取，不在日常构建中下载完整历史。

### 历史策略

在线 BetterMindustry 仓库采用独立根提交，从 v160.7 源码树开始，不包含 Mindustry 的完整 Git 祖先历史。

原因：

- BetterMindustry 是持续分化的大型分支，商业与玩法路线不再以自动合并上游为目标。
- 完整历史约 1.9 GiB，会显著增加首次推送、克隆和 CI 成本。
- 上游提交号、许可证和源码快照足以保留来源与 GPL 义务。

本地保留 `archive/mindustry-full-history` 分支供追溯。后续上游版本使用源码差异、补丁和人工审查同步，不把完整历史并入在线主干。

## 工具链

上游 README 写明编译必须使用 JDK 17，`settings.gradle` 拒绝低于 17 的 Java，`build.gradle` 使用 `--release 17`。当前项目以 JDK 17 构建。

最新桌面客户端源码会提示用户安装 JDK 25。源码注释同时说明当前实际最低版本仍是 17，25 是官方推荐并可能在未来成为硬要求。服务端基线已在 JDK 17 下完成启动测试。

构建时显式设置 `JAVA_HOME`，避免 PATH 漂移。

## 基线验证

2026-10-22 在 Windows 11、Temurin JDK 17.0.20.1、Gradle 9.3.1 下验证：

- [x] `.\gradlew.bat tests:test`
- [x] `.\gradlew.bat server:dist`
- [x] `.\gradlew.bat desktop:dist`
- [x] 服务端本地启动并正常退出
- [ ] 原版客户端人工连接

测试共 335 个，0 失败、0 错误、0 跳过。

产物：

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| `server/build/libs/server-release.jar` | 19,401,038 字节 | `372AE5B8FBF122692169ADD9124E4446CA47B424335130E6A91F76667D02B936` |
| `desktop/build/libs/Mindustry.jar` | 88,646,070 字节 | `A399F2A3B3408AD8D12429F1F44B239B873D9B0EBDC5520C890108457A3D57C8` |

服务端启动时提示当前为 custom build，因此客户端版本检查关闭。M1 开始构造自定义服务端时应传入明确的构建版本，不能继续使用无版本自定义构建。
