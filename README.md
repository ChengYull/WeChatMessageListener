# 微信群聊发言统计器 (WeChatMessageListener)

基于 Android `NotificationListenerService` 的微信群聊发言统计应用。通过监听微信消息通知提取群名与发言人，去重后写入本地 Room 数据库，并提供三级浏览：群列表 → 成员排行 → 消息明细。

## 工作原理

微信群消息触发系统通知时：

- **群聊**：通知标题为群名，正文为 `[N条]昵称:消息`（折叠时带 `[N条]` 前缀）。
- **私聊**：通知标题为对方昵称，正文为消息内容。

本应用优先使用 `Notification.MessagingStyle`（`EXTRA_MESSAGES`）解析——若微信以该样式发布通知，可把折叠的 `[N条]` 展开为 N 条独立消息，每条带各自的 sender；回退到文本解析：剥掉 `[N条]` 前缀后按第一个冒号拆分 `昵称:消息`。

去重采用 `SHA-256(groupName | sender | text | 秒级时间戳)` 作为唯一键，写入 Room 时 `IGNORE` 冲突策略跳过重复。

## 功能

- 群列表：按群名分组，显示各群消息总数；"全部"模式下显示日历热力图（按月展示每日发言频次增减趋势）
- 成员排行：进入某群后显示成员及其发言次数（降序）
- 消息明细：点击成员查看其全部消息内容与时间
- 按天统计：日期 Chip 条切换（最近 14 天），支持查看每日独立统计，也可切回"全部"查看全量累计数据
- 发言曲线图：选中某日后，以 5 分钟为粒度展示发言频次折线图（支持触摸查看具体数值），覆盖主页/群聊/成员三级
- 日历热力图：在"全部"模式下按月显示每日发言频次，红色=比前一天多，绿色=比前一天少，支持左右点击切换月份
- 导入/导出：三个界面均支持 JSON 格式导入导出，导入时自动校验文件类型匹配与去重
- 实时刷新：基于 Room `Flow`，新消息入库即更新 UI
- 一键开启通知监听权限、清空统计

## 环境要求

- Android 7.0 (API 24) 及以上
- compileSdk 36 (API 36.1)
- AGP 9.2.1 + Gradle 9.4.1 + Kotlin (内置) + KSP 2.1.20-2.0.1 + Room 2.7.1

## 构建与运行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 产物路径
app/build/outputs/apk/debug/app-debug.apk

# 安装到已连接设备
./gradlew installDebug
```

> Windows 环境使用 `./gradlew`（Unix 脚本），在 Git Bash / WSL 中运行。

## 使用步骤

1. 安装 APK 后启动"群聊统计器"。
2. 点击 **"去开启通知监听权限"**，在系统设置的通知访问列表中找到本应用并开启。
3. 返回应用，按钮文案应变为"通知监听已开启"。
4. 确保目标群聊在微信内的通知处于开启状态。
5. 群内有新消息时，应用排行榜自动刷新。
6. 默认显示全量统计；点击顶部日期 Chip 条可按天查看每日独立排行，点击"全部"切回累计数据。

## 项目结构

```
app/src/main/java/com/example/wechatstats/
├── MainActivity.kt                  # 群列表界面（含日期 Chip 条 + 全部群合计曲线图）
├── MemberListActivity.kt            # 群成员排行界面（含该群发言曲线图）
├── MessageListActivity.kt           # 成员消息明细界面（含该用户发言曲线图）
├── Adapters.kt                      # Group/Member/Message 三个 ListAdapter
├── DateAdapter.kt                   # 日期 Chip 条适配器
├── StatsChartView.kt                # 自定义 Canvas 曲线图 View（零外部依赖）
├── CalendarHeatmapView.kt           # 自定义 Canvas 日历热力图 View（零外部依赖）
├── WeChatNotificationListener.kt    # NotificationListenerService 核心解析
└── data/
    ├── DateUtils.kt                 # 日期工具类（起止毫秒、格式化等）
    ├── MessageRecord.kt             # Room 实体 (含 groupName/sender/text/timestamp)
    ├── MessageDao.kt                # groupsFlow/membersFlow/messagesFlow/chartFlow（含按天过滤重载）
    ├── AppDatabase.kt               # Room 单例
    ├── StatsRepository.kt           # DAO 封装
    ├── ExportUtils.kt                # JSON 导出
    ├── ImportUtils.kt                # JSON 导入解析（格式校验 + 界面匹配）
    ├── GroupRow.kt                  # 群聚合结果 POJO
    ├── StatsRow.kt                  # 成员聚合结果 POJO
    └── ChartPoint.kt                # 5 分钟分桶 POJO（bucketStartMillis + count）
```

## 已知局限

- **群通知折叠**：依赖微信以 `MessagingStyle` 发布通知才能正确展开 `[N条]`。若微信未使用该样式，折叠通知可能只统计到 1 条。可用 `adb logcat -s WeChatStatsListener` 查看实际收到的字段，日志会打印 `msg-style:` 或 `text-parse:` 前缀以便核对。
- **关闭群通知即无法统计**：用户若在系统中关闭目标群聊的通知，该群消息完全捕获不到——这是 `NotificationListenerService` 方案的固有局限。
- **秒级去重**：同一秒内同一发言人发送相同内容的多条消息会被误判为一条。群聊场景概率极低，可接受。

## 权限说明

仅申请最小必要权限：

- `BIND_NOTIFICATION_LISTENER_SERVICE`：系统绑定给通知监听服务，用户需在设置中显式授权。
- 不申请 `POST_NOTIFICATIONS`、不做前台保活、不申请网络权限。

## 技术栈

- Kotlin + Android 原生
- Room 2.7.1 (KSP 代码生成)
- kotlinx-coroutines + Flow
- AndroidX AppCompat / RecyclerView / Lifecycle
- Material Components

## 许可

本项目仅供学习与个人使用。使用本应用监听通知前，请确保遵守当地法律法规及微信用户协议。
