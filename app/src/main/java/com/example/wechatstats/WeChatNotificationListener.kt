package com.example.wechatstats

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
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
        // 群汇总通知不直接入库，等待子通知逐条触发
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE)?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()).orEmpty()

        if (sender.isEmpty()) return
        if (isSummaryTitle(sender)) return

        val timestamp = sbn.postTime
        saveRecord(sender, text, timestamp)
    }

    private fun isSummaryTitle(title: String): Boolean {
        // 形如 "张三 等 5 条新消息" 的折叠标题
        return title.contains("等") && title.contains("条新消息")
    }

    private fun saveRecord(sender: String, text: String, postTime: Long) {
        val key = dedupKey(sender, text, postTime)
        serviceScope.launch {
            try {
                repository.insert(
                    MessageRecord(
                        notificationKey = key,
                        sender = sender,
                        text = text,
                        timestamp = postTime
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "insert failed for $sender", e)
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

        private fun dedupKey(sender: String, text: String, postTime: Long): String {
            val secondPrecision = postTime / 1000
            val raw = "$sender|$text|$secondPrecision"
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun isEnabled(context: Context): Boolean {
            val componentName = android.content.ComponentName(context, WeChatNotificationListener::class.java)
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.split(":").any { it == componentName.flattenToString() }
        }
    }
}
