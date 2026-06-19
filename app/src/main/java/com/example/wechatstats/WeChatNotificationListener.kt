package com.example.wechatstats

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.MessageRecord
import com.example.wechatstats.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest

class WeChatNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: StatsRepository

    override fun onCreate() {
        super.onCreate()
        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WECHAT_PACKAGE) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = notification.extras.getString(Notification.EXTRA_TITLE)?.trim().orEmpty()
        if (title.isEmpty() || isSummaryTitle(title)) return

        val groupName = title

        // 优先走 MessagingStyle：可拿到折叠通知里每条消息的独立 sender
        val msgs = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (msgs != null && msgs.isNotEmpty()) {
            var handled = false
            for (p in msgs) {
                val m = p as? Notification.MessagingStyle.Message ?: continue
                val sender = m.senderPerson?.name?.toString()?.trim().orEmpty()
                if (sender.isEmpty()) continue
                val text = m.text?.toString().orEmpty()
                Log.d(TAG, "msg-style: group=$groupName sender=$sender text=$text")
                saveRecord(groupName, sender, text, m.timestamp)
                handled = true
            }
            if (handled) return
        }

        // 回退：解析 EXTRA_TEXT，格式形如 "[3条]昵称:消息" 或 "昵称:消息"
        val rawText = (notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()).orEmpty()
        Log.d(TAG, "text-parse: title=$title raw=$rawText")

        val parsed = parseText(rawText)
        if (parsed.hasSenderPrefix) {
            saveRecord(groupName, parsed.sender, parsed.text, sbn.postTime)
        } else {
            // 私聊：标题即对方昵称，文本是消息内容
            saveRecord(groupName, title, rawText.trim(), sbn.postTime)
        }
    }

    private data class ParsedText(val sender: String, val text: String, val hasSenderPrefix: Boolean)

    private fun parseText(raw: String): ParsedText {
        var text = raw.trim()
        // 剥掉 "[N条]" / "［N条］" 折叠前缀
        text = Regex("^[\\[［]\\s*\\d+\\s*条\\s*[\\]］]\\s*").replace(text, "")
        // 找第一个冒号（半角或全角），且前缀长度合理（昵称一般 ≤ 30 字）
        val colonIdx = text.indexOfFirst { it == ':' || it == '：' }
        if (colonIdx in 1..30) {
            val sender = text.substring(0, colonIdx).trim()
            val msg = text.substring(colonIdx + 1).trim()
            if (sender.isNotEmpty()) return ParsedText(sender, msg, true)
        }
        return ParsedText("", text, false)
    }

    private fun isSummaryTitle(title: String): Boolean {
        return title.contains("条新消息")
    }

    private fun saveRecord(groupName: String, sender: String, text: String, postTime: Long) {
        val key = dedupKey(groupName, sender, text, postTime)
        serviceScope.launch {
            try {
                repository.insert(
                    MessageRecord(
                        notificationKey = key,
                        groupName = groupName,
                        sender = sender,
                        text = text,
                        timestamp = postTime
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "insert failed: $groupName/$sender", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "WeChatStatsListener"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        private fun dedupKey(groupName: String, sender: String, text: String, postTime: Long): String {
            val secondPrecision = postTime / 1000
            val raw = "$groupName|$sender|$text|$secondPrecision"
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun isEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, WeChatNotificationListener::class.java)
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.split(":").any { it == componentName.flattenToString() }
        }
    }
}
