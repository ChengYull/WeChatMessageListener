# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指引。

## 项目概述

微信群聊发言统计 App：通过 `NotificationListenerService` 监听微信（`com.tencent.mm`）消息通知，提取发言人昵称，去重后写入本地 Room 数据库，并在 App 内展示发言排行榜。原始设计规格在 `app/需求文档.txt`（基于 AccessibilityService，已废弃；当前实现改用通知监听以避免依赖微信内部 view 层级）。

包名统一为 `com.example.wechatstats`。

## 构建与测试

Windows 主机，bash shell——使用 `./gradlew`（Unix 脚本）而非 `gradlew.bat`。

- 构建：`./gradlew assembleDebug`
- 安装到已连接设备：`./gradlew installDebug`
- 单元测试：`./gradlew test`
- 运行单个单元测试类：`./gradlew test --tests "com.example.wechatstats.ExampleUnitTest"`
- 插桩测试：`./gradlew connectedAndroidTest`（需要连接设备/模拟器）
- 清理：`./gradlew clean`

## 工具链说明

- AGP 9.2.1，Gradle Kotlin DSL，版本目录在 `gradle/libs.versions.toml`。
- AGP 9 **内置 Kotlin**，因此 `app/build.gradle.kts` **不**应用 `org.jetbrains.kotlin.android` 插件——重复 apply 会报 `extension already registered with that name 'kotlin'`。Kotlin 源码由内置工具链直接编译，无需 `kotlinOptions { jvmTarget }` DSL。
- KSP 用于 Room 编译期代码生成。KSP 版本必须与内置 Kotlin 版本匹配，否则报 `unexpected jvm signature V`——当前用 KSP `2.1.20-2.0.1` + Room `2.7.1`。
- `gradle.properties` 中 `android.disallowKotlinSourceSets=false` 是为兼容 KSP 向 `kotlin.sourceSets` 注入生成源码（内置 Kotlin 默认禁止此操作）。
- `compileSdk` 使用新语法 `release(36) { minorApiLevel = 1 }`（API 36.1）。`minSdk = 24`，`targetSdk = 36`。Java 11 source/target。
- `settings.gradle.kts` 优先通过阿里云镜像解析仓库，其后才是 Google/Maven Central。`dependencyResolutionManagement` 设置为 `FAIL_ON_PROJECT_REPOS`——不要在模块内添加 `repositories { }` 块。
- `local.properties` 将 `sdk.dir` 指向 `E:\AndroidSDK`（本机路径，已 gitignore）。
- 已开启 `org.gradle.configuration-cache=true`——修改构建脚本或设置会使缓存失效。

## 架构

源码位于 `app/src/main/java/com/example/wechatstats/`：

- `WeChatNotificationListener.kt`——`NotificationListenerService` 子类。`onNotificationPosted` 中过滤 `com.tencent.mm`，跳过 `FLAG_GROUP_SUMMARY` 折叠汇总通知与形如"X 等 N 条新消息"的标题，从 `notification.extras` 取 `EXTRA_TITLE`（发言人）与 `EXTRA_TEXT`/`EXTRA_BIG_TEXT`（内容）。用 `SHA-256(sender|text|秒级 postTime)` 作为 `notificationKey` 去重，通过 `Dispatchers.IO` 协程作用域入库。`companion object.isEnabled(context)` 反射 `Settings.Secure` 的 `enabled_notification_listeners` 判断本服务是否已授权。
- `data/MessageRecord.kt`——Room 实体，`notificationKey` 上建唯一索引用于 insert-ignore 去重。
- `data/MessageDao.kt`——`insert`（IGNORE 冲突策略）、`statsFlow()`（按 sender 聚合 + 计数降序的 `Flow<List<StatsRow>>`）、`clear()`。
- `data/AppDatabase.kt`——Room 单例，db 名 `wechat_stats_db`。
- `data/StatsRepository.kt`——DAO 薄封装。
- `data/StatsRow.kt`——聚合查询结果 POJO（`nickname` + `count`）。
- `ui/StatsViewModel.kt`——`AndroidViewModel`，`statsFlow` 经 `stateIn` 转为 `StateFlow`，提供 `clear()`。
- `MainActivity.kt`——RecyclerView 排行榜，`lifecycleScope` 收集 `StateFlow`；`FLAG_KEEP_SCREEN_ON`；按钮跳转 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`；`onResume` 时根据授权状态切换按钮文案。
- `StatsAdapter.kt`——`ListAdapter<StatsRow, ...>` + DiffUtil。

Manifest 注册 `MainActivity`（LAUNCHER）与带 `BIND_NOTIFICATION_LISTENER_SERVICE` 权限的 `<service>`。**不**包含任何 AccessibilityService 配置、`POST_NOTIFICATIONS`、前台服务权限。

## 已知局限

- **群通知折叠**：当前靠 `FLAG_GROUP_SUMMARY` 跳过 summary、依赖子通知逐条触发 `onNotificationPosted`。真实微信折叠行为未实测，首轮真机测试需确认计数准确。
- **关闭群通知即无法统计**：用户若在系统中关闭目标群通知，则该群消息完全捕获不到——这是 NotificationListenerService 方案的固有局限。
- **秒级去重**：同一秒内同发言人同内容的多条消息会被误判为一条。群聊场景概率极低，可接受。

## Git 操作规范

本仓库是**本地仓库**（无远端，分支 `master`）。每次完成一组修改或操作后，**必须**主动创建提交，不需要等待用户指令。

流程：

1. `git status` 查看待提交变更，`git diff` 复核改动。
2. 按逻辑分组暂存——用 `git add <具体文件>` 而非 `git add -A`/`git add .`，避免误纳入 `.claude/`、`local.properties`、构建产物等。`.claude/`、`local.properties`、`build/`、`.gradle/`、`.idea/` 已在 `.gitignore` 中。
3. 提交信息遵循中文 + 简明祈使句风格（如"改用 NotificationListenerService 实现群聊统计"），聚焦"为什么"而非逐项罗列文件。提交信息末尾追加 `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`。
4. 通过 HEREDOC 传递 commit message 以保证格式：

   ```bash
   git commit -m "$(cat <<'EOF'
   提交信息

   Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
   EOF
   )"
   ```

5. 提交后 `git log --oneline -5` 复核。
6. **不要**执行 `git push`（本地仓库无远端）、`git reset --hard`、`git rebase -i`、`--no-verify`、`--amend` 等破坏性或交互式操作，除非用户明确要求。
7. 若一次任务包含多个独立逻辑单元（如"配置依赖"和"实现数据层"），应分多次提交而非一次大提交。
