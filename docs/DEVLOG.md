# DEVLOG

> 最新在上。历史记录（WebView+容器工程 R1~R4/M 系列）见 tag `legacy-webview-0.2.1`。

## N3 + N4 · 设备控制与启动台（2026-11-06 深夜）

**N3a N3a**：无障碍服务核心。`isEnabled` 查 Settings.Secure 不信任内存单例（系统随时重绑）；
观察=BFS≤300 节点紧凑 JSON（受保护包只给错误不给树）；动作=performAction 语义操作+全局 back/home；
快照校验防陈旧目标。PhonePolicy：受保护包/支付银行正则/敏感标签 [redacted] 脱敏。
phone_observe / phone_action 接入 agent 循环。设备页 ContentObserver 实时刷新+系统设置引导。

**N3b N3b**：ScreenCaptureService（FGS mediaProjection：surface 按需挂载、JPEG70≤1280px、
Base64 仅驻内存、grant 一次性）；phone_capture 截图以临时 user 图片块随本轮发模型（SessionStore
落盘前剥离 imageBase64）；phone_gesture 坐标手势（越界/受保护窗口拦截，dispatchGesture 包
suspendCancellableCoroutine）；ChatClient 支持多模态 content；设备页截屏授权链式引导。

**N4**：启动台=状态卡矩阵（模型 API/无障碍/截取/工作区会话）+吉祥物头图+免责声明，卡片直达对应页。

**踩坑**：① AGP9 内置 Kotlin——`org.jetbrains.kotlin.android` 插件已废弃，加了就报错；
② Windows 8.3 短路径（ADMINI~1）下 `relativeTo` 产出 `..\..\` 链 → relOf 用 canonical 前缀截取；
③ SseAssembler 回调在普通 lambda 里不能调 suspend send → trySend + buffer(UNLIMITED)；
④ ContentObserver 是抽象类要 object 匿名实现；⑤ Compose launcher 声明顺序（前向引用不合法）。

**待办（N5）**：真机 e2e（等 Redmi 插 USB；当前 adb 只有台 Android 9 网络设备）；TYPE_ACCESSIBILITY_OVERLAY
运行状态条（backlog）；推送前问用户（LICENSE 建议改 AGPL-3.0——深度参考 AGPL 的 dsh-go）。

## N1 · 对话内核（2026-11-06）

N1。ProviderStore（EncryptedSharedPreferences 全量加密，Keystore 坏=功能禁用不降级明文，
预置智谱开放平台条目）；SessionStore（每会话一文件+index，tmp+rename 原子写）；ChatClient
（手写 SSE：假流式 peek 探测、[DONE]/finish_reason 双收口、trySend 防取消崩溃、禁重定向防付费
POST 重放、cancelActive 显式断连）；设置页供应商增删改+GET /models 三格式兼容；聊天屏流式气泡+
会话抽屉+发送/停止。SseAssembler 单测 6 用例。

## N0 · 纯原生重写奠基（2026-11-06）

**决策**：用户拍板推倒重来——旧工程是 DSHA 分叉（WebView+Ubuntu 容器），dsha 元素除不净、
低端机内存压力大。新技术路线参照 **dsharness-go**（研读报告：`..\00-决策\dsharness-go技术研读与原生重写方案.md`，
源码参照：`D:\ZcodeRely\research\dsharness-go`）：Kotlin+Compose 纯原生、API 直连、文件沙箱、
无障碍控制手机。GitHub 仓库已转 private。先做独立模式；连电脑端 agent 列二期。

**保全**：tag `legacy-webview-0.2.1` + 分支 `legacy/webview-container`（指向 09f31d1），
随后 git rm 旧工程全部代码（2069 文件）；`shots\`（旧验证截图）untracked 保留在磁盘。

**新骨架**：
- gradle 基线：AGP 9.1.1（缓存命中）+ Kotlin 2.3.21 + Compose BOM 2026.06.00 + compileSdk 36 / minSdk 29
- applicationId 不变 com.zcodeandriod.app（与手机上旧包同 debug 签名可覆盖升级），versionCode 300 / versionName 0.3.0-native.a1
- 分层 core/data/tools/agent/ui/notifications；enum 路由四屏（对话/设备/启动台/设置）；主题令牌→M3 映射
- 通知渠道集中注册（AppNotifications）
- 图标资产沿用吉祥物成品（背景色 #0D0E0F 改名 zcodea_launcher_background，去 monochrome）
- 构建入口 `downloads\android-build\zcodea-build.cmd`（复用 gradle-home 缓存 + init.gradle 直连 dl.google.com）

**遗留**：LICENSE 现为 MIT；新代码深度参考 AGPL-3.0 的 dsharness-go，推送前建议征得用户同意改为 AGPL-3.0。

