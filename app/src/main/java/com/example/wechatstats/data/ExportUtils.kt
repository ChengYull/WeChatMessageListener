package com.example.wechatstats.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExportUtils {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun exportGroup(
        context: Context,
        groupName: String,
        messages: List<MessageRecord>,
        dayStart: Long,
        dayEnd: Long
    ): Uri? {
        val dateLabel = dateRangeLabel(dayStart, dayEnd)
        val fileName = "${sanitize(groupName)}_${dateLabel}.json"
        val json = buildJson(groupName, null, dayStart, dayEnd, messages)
        return writeJson(context, fileName, json)
    }

    fun exportSender(
        context: Context,
        groupName: String,
        sender: String,
        messages: List<MessageRecord>,
        dayStart: Long,
        dayEnd: Long
    ): Uri? {
        val dateLabel = dateRangeLabel(dayStart, dayEnd)
        val fileName = "${sanitize(groupName)}_${sanitize(sender)}_${dateLabel}.json"
        val json = buildJson(groupName, sender, dayStart, dayEnd, messages)
        return writeJson(context, fileName, json)
    }

    private fun buildJson(
        groupName: String,
        sender: String?,
        dayStart: Long,
        dayEnd: Long,
        messages: List<MessageRecord>
    ): String {
        val root = JSONObject()
        root.put("type", if (sender != null) "messages" else "group")
        // 主界面导出时 groupName 传空字符串，从第一条消息中补上
        val actualGroupName = if (groupName.isEmpty()) messages.firstOrNull()?.groupName.orEmpty() else groupName
        root.put("groupName", actualGroupName)
        if (sender != null) root.put("sender", sender)
        root.put("dateRange", dateRangeLabel(dayStart, dayEnd))
        root.put("exportTime", TIME_FMT.format(java.time.LocalDateTime.now()))
        root.put("totalMessages", messages.size)

        val arr = JSONArray()
        // 批量构建 JSONArray——避免每条消息都 new JSONObject + put
        for (m in messages) {
            val obj = JSONObject()
            obj.put("s", m.sender)
            obj.put("t", m.text)
            obj.put("ts", m.timestamp)
            arr.put(obj)
        }
        root.put("messages", arr)

        return root.toString()
    }

    private fun writeJson(context: Context, fileName: String, content: String): Uri? {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun dateRangeLabel(dayStart: Long, dayEnd: Long): String {
        if (dayStart == -1L) return "all"
        return LocalDate.ofEpochDay(dayStart / 86400000).format(DATE_FMT)
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}
