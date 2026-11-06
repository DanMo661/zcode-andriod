# 构建说明

## 环境

| 项 | 要求 |
|---|---|
| JDK | 21（toolchain 锁 21） |
| Android SDK | Platform 36 + Build-Tools 36.0.0 |
| Gradle | 9.3.1（wrapper 自带，sha256 锁定） |
| AGP / Kotlin | 9.1.1 / 2.3.21（见 `gradle/libs.versions.toml`） |

## 本机（绿色环境）

```cmd
D:\ZcodeRely\ZCODEAndriod\downloads\android-build\zcodea-build.cmd assembleDebug
```

- 脚本内部设置 `JAVA_HOME`（绿色 JDK21）、`ANDROID_HOME`（主 SDK）、`GRADLE_USER_HOME`（共享依赖缓存）
- `-I init.gradle` 把 `dl.google.com/android/maven2` 直连仓库插到 `google()` 之前（绕 maven.google.com 被墙）
- mavenCentral / gradlePluginPortal 本机可直连，无需代理

## 标准 Android 环境

```bash
./gradlew assembleDebug          # 构建
./gradlew testDebugUnitTest      # 单元测试
```

`local.properties` 的 `sdk.dir` 或环境变量 `ANDROID_HOME` 指向 SDK 即可。

## 产物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- CI：push 到 main 自动构建并上传 artifact（见 `.github/workflows/android.yml`）

## 纪律

- 签名密钥、API key 不入库、不进日志
- 版本号在 `app/build.gradle.kts`：versionCode 递增、versionName `0.3.x-native.<里程碑>`
