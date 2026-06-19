# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指引。

## 项目状态

这是一个尚未完成的 Android 项目脚手架：`app/src/main/java/` 下还没有任何 Kotlin 源码（仅 New Project 向导生成的测试桩和资源文件）。计划中的产品在 `app/需求文档.txt` 中描述——一个基于 Android AccessibilityService + Room 实现的微信群聊（com.tencent.mm）发言统计 App。实现功能时请将该文档作为设计规格。注意：文档中使用的是包名 `com.example.wechatstats`，而 `app/build.gradle.kts` 当前的 `namespace`/`applicationId` 设为 `com.example.myapplication`；写代码前请先统一这两者，不要假设其中任何一个为准。

## 构建与测试

Windows 主机，bash shell——使用 `./gradlew`（Unix 脚本）而非 `gradlew.bat`。

- 构建：`./gradlew assembleDebug`
- 安装到已连接设备：`./gradlew installDebug`
- 单元测试：`./gradlew test`
- 运行单个单元测试类：`./gradlew test --tests "com.example.myapplication.ExampleUnitTest"`
- 插桩测试：`./gradlew connectedAndroidTest`（需要连接设备/模拟器）
- 清理：`./gradlew clean`

## 工具链说明

- AGP 9.2.1，Gradle Kotlin DSL，版本目录在 `gradle/libs.versions.toml`。
- `compileSdk` 使用新语法 `release(36) { minorApiLevel = 1 }`（API 36.1）。`minSdk = 24`，`targetSdk = 36`。
- Java 11 source/target。Kotlin 插件**目前未**在 `app/build.gradle.kts` 中应用；若要添加 Kotlin 源码，需按需求文档补充 `org.jetbrains.kotlin.android`（以及 Room 所需的 `com.google.devtools.ksp`）。
- `settings.gradle.kts` 优先通过阿里云镜像解析仓库，其后才是 Google/Maven Central。`dependencyResolutionManagement` 设置为 `FAIL_ON_PROJECT_REPOS`——不要在模块内添加 `repositories { }` 块。
- `local.properties` 将 `sdk.dir` 指向 `E:\AndroidSDK`（本机路径，已 gitignore）。
- 已开启 `org.gradle.configuration-cache=true`——修改构建脚本或设置会使缓存失效。

## 架构（目标形态，依据需求文档）

计划在 `app/src/main/java/com/example/wechatstats/` 下的包结构：

- `WeChatMonitorService.kt`——作用于 `com.tencent.mm` 的 `AccessibilityService`。窗口内容变化时遍历活动窗口，找到聊天 `RecyclerView`，对每条新消息节点提取发言人昵称（当前用了一个脆弱的文本节点启发式逻辑——文档明确要求改用 `findAccessibilityNodeInfosByViewId("com.tencent.mm:id/...")`，先用 Layout Inspector 观察微信真实布局）。每个唯一昵称 upsert 入库，并将其计数加一。
- `data/`——Room 配置：`UserStats` 实体（主键 = 昵称）、`StatsDao`（insert-ignore + 计数自增 + 按计数降序返回的 `Flow<List<UserStats>>`）、`AppDatabase` 单例。
- `MainActivity.kt` + `StatsAdapter.kt`——基于 RecyclerView 的排行榜，通过 `lifecycleScope` 收集 DAO 的 Flow。MainActivity 还保持屏幕常亮（`FLAG_KEEP_SCREEN_ON`），并提供按钮跳转到 `Settings.ACTION_ACCESSIBILITY_SETTINGS`。
- `res/xml/accessibility_config.xml`——服务配置，过滤到 `com.tencent.mm`，事件类型 `typeWindowContentChanged|typeWindowStateChanged`。

实现时还需更新 `AndroidManifest.xml`，注册 `MainActivity`（LAUNCHER）以及带 `BIND_ACCESSIBILITY_SERVICE` 权限和配置 meta-data 的无障碍 `<service>`——当前 manifest 仅声明了 `<application>` 块。

## 无障碍服务注意事项

需求文档明确指出了以下几点；修改 `WeChatMonitorService` 时请务必遵守：

- 发言人提取逻辑是最脆弱的部分。不要依赖位置启发式——用 Layout Inspector 在真机上抓取真实的 `com.tencent.mm:id/...` resource-id，再切换到 `findAccessibilityNodeInfosByViewId`。
- 去重仅靠单个 `lastProcessedMessage` 字符串。如果一次处理超过底部最后一条消息，会造成计数错误；扩展扫描深度前请先重新设计去重。
- 线上稳定性依赖 OEM 的电池/自启动设置（"无限制" / 不优化电池）——这是运行时的设置问题，App 自身无法强制保证。
