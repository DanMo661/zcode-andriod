<p align="center"><img src="branding/mascot/mascot-square-512.png" width="96" alt="ZcodeAndriod"></p>

# ZcodeAndriod（ZcodeA）

**跑在手机上的原生 AI coding 助手** —— Kotlin + Jetpack Compose 纯原生实现，
无容器、无 root、点开即用。

> **声明**：本项目为独立第三方作品，与智谱（Z.ai）、ZCode、DeepSeek 及 DeepSeek Harness
> 均无隶属关系，也未使用其官方标识。吉祥物图片授权信息见
> [branding/mascot/SOURCES.md](branding/mascot/SOURCES.md)。

## 能做什么

| 能力 | 说明 | 状态 |
|---|---|---|
| AI 对话 | 直连 OpenAI 兼容 API（自配供应商），SSE 流式输出 | 建设中 |
| Coding 沙箱 | AI 在手机私有工作区读写文件、抓取网页 | 建设中 |
| AI 控制手机 | 无障碍观察屏幕语义 + 按需截屏，AI 替你操作设备 | 建设中 |
| 启动台 | API / 设备授权 / 工作区状态统一控制台 | 建设中 |

## 技术栈

- Kotlin 2.3 + Jetpack Compose（Material3，明暗双主题）
- OkHttp 手写 SSE 流式解析 · kotlinx-serialization
- EncryptedSharedPreferences 存凭据 · WorkManager / 前台服务执行
- minSdk 29 · targetSdk 36 · arm 全架构

## 构建

见 [docs/BUILDING.md](docs/BUILDING.md)。CI 在每次 push 到 main 时自动构建并上传 debug APK。

## 许可

[AGPL-3.0](LICENSE) · Copyright (C) 2026 DanMo661
第三方组件与吉祥物授权见 [THIRD-PARTY-NOTICE.md](THIRD-PARTY-NOTICE.md)。

## 路线图

- **N1** 对话内核（供应商配置 + 流式聊天）
- **N2** 工具循环 + 文件沙箱（read/write/list + http_get）
- **N3** 设备控制（无障碍 + 截屏 + 四手机工具）
- **N4** 启动台 + 打磨
- **N5** 真机验收
- 二期：手机连接电脑端 agent（完整 coding 能力远程执行）
