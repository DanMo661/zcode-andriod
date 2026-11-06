# THIRD-PARTY-NOTICE — 第三方组件与资产声明

ZcodeAndriod 是**独立第三方开源项目**，与智谱（Z.ai）、ZCode、DeepSeek 及 DeepSeek Harness 均无隶属关系，也未使用其官方标识。本项目代码以 **AGPL-3.0** 许可发布（见根目录 `LICENSE`），Copyright (C) 2026 DanMo661。

## 开源依赖

| 依赖 | 许可 |
|---|---|
| Kotlin / kotlinx-coroutines / kotlinx-serialization | Apache-2.0 |
| Jetpack Compose / AndroidX（activity, lifecycle, core, security-crypto, compose BOM） | Apache-2.0 |
| OkHttp / MockWebServer | Apache-2.0 |
| JUnit | EPL-1.0 |

依赖坐标与版本锁定见 `gradle/libs.versions.toml`。

## 启动图标吉祥物（社区拟人形象，非官方）

启动图标与启动台头像采用社区画师 **ZipZipPipe**（[Pixiv](https://www.pixiv.net/users/18604994) / [Bilibili](https://space.bilibili.com/4168597)）创作的 "GLM 娘" 智谱 GLM 社区拟人形象（原绘：Pixiv《[AI娘化](https://www.pixiv.net/artworks/148186519)》p6，经 [shuaizenge/ai-girls](https://github.com/shuaizenge/ai-girls) 收录获取）。作者公开声明其图片开放分享、允许再创作；本项目为非营利开源用途，保留署名。完整出处链、sha256 与派生记录见 `branding/mascot/SOURCES.md`。该形象为社区创作，**不代表智谱/Z.ai 官方角色**。

## 致谢

本项目的移动端 Agent 架构（无障碍设备控制、流式工具循环等）在设计上参考了 AGPL-3.0 开源项目 [DSHarness Go](https://github.com/mionio233-collab/dsharness-go) 的公开快照，据此独立实现，未复制其源码；依 AGPL-3.0 精神以同协议回馈开源社区。
