# ZcodeAndriod（ZcodeA）工程规范 — agents 必读

原生 Kotlin + Compose 安卓端 AI coding 助手。**纯原生路线**：无容器、无 WebView 壳、无 root。
旧 WebView+容器工程已归档：tag `legacy-webview-0.2.1` / 分支 `legacy/webview-container`。

## 一、命名与商标红线（最高优先级）

1. 产品名 **ZcodeAndriod**，缩写 **ZcodeA**，包名 `com.zcodeandriod.app`。
2. 源码、资源、文档里**禁止出现 dsha / deepseekharness / dsh** 字样（资产引用除外，见 branding）。
3. 必须保留「非官方声明」：本项目与智谱（Z.ai）/ ZCode 官方无隶属关系；README 与 About 页声明不得删。
4. 不复制 ZCode 官方 logo/图标。吉祥物图片来源与授权只记录在 `branding/mascot/SOURCES.md`，勿改写。

## 二、架构分层（包 com.zcodeandriod.app）

```
core/    网络(SSE 手写解析)、执行槽(RunSlots+前台服务持有 scope)、策略、沙箱
data/    @Serializable 模型、ProviderStore(EncryptedSharedPreferences)、SessionStore(每会话一文件)
tools/   LocalTool 接口 + 文件/HTTP/手机工具（闭式 allowlist，双重校验）
agent/   无障碍服务、MediaProjection 截屏、悬浮状态条(TYPE_ACCESSIBILITY_OVERLAY)
ui/      App.kt enum 路由；每屏一个文件；theme/ 是唯一取色处（其余引用 MaterialTheme.colorScheme）
notifications/  通知渠道唯一注册处
```

- 学 dsharness-go（research\dsharness-go）：SSE 边界处理、无障碍骨架、四手机工具+snapshotId、安全策略。
- 戒其坏味道：1345 行 God Object、字符串魔法路由、会话 JSON 塞 SharedPreferences、通知渠道散建。
- 新文件 >400 行先拆；新增能力先进 tools/ 的工具协议，不绕过 agent 循环。

## 三、构建与验证

- 构建入口：`cmd //c "D:\ZcodeRely\ZCODEAndriod\downloads\android-build\zcodea-build.cmd" assembleDebug`
- 里程碑收口必须构建绿；交付型改动收尾跑一轮 nitpick。
- 真机：adb 在 `downloads\platform-tools\platform-tools\adb.exe`，Git Bash 必须 `MSYS_NO_PATHCONV=1`，push 源路径 D:\ 风格。
- 测试机：Redmi M2104K10AC（Android 13，序列 x8y5sclbmfdyd6uw）；**模拟器 15be5f38 勿碰**。

## 四、安全纪律

- API key 只进 EncryptedSharedPreferences；不进代码、不进 commit、不进日志。
- 手机控制工具必须保留安全策略：支付/凭据正则拦截、敏感节点脱敏、坐标手势只从语义节点推导。
- 每轮工具循环上限 40 轮、单轮 ≤12 个 tool call，动作后强制 re-observe。

## 五、协作纪律

- 主会话决策 + 子代理干活；改动前读本文件；构建绿再报完成。
- push 前必须征得用户同意（`git -c http.proxy=http://127.0.0.1:7897 push`）。
- 每里程碑结束更新 `docs/DEVLOG.md`（新条目在最上）。
