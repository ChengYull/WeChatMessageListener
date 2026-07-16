package com.example.wechatstats.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val TS_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun exportGroup(
        context: Context,
        groupName: String,
        messages: List<MessageRecord>,
        dayStart: Long,
        dayEnd: Long
    ): Uri? {
        val dateLabel = dateRangeLabel(dayStart, dayEnd)
        val fileName = "${sanitize(groupName)}_${dateLabel}.json"

        val json = buildJson(
            groupName = groupName,
            sender = null,
            dayStart = dayStart,
            dayEnd = dayEnd,
            messages = messages
        )

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

        val json = buildJson(
            groupName = groupName,
            sender = sender,
            dayStart = dayStart,
            dayEnd = dayEnd,
            messages = messages
        )

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
        root.put("groupName", groupName)
        if (sender != null) root.put("sender", sender)
        root.put("dateRange", dateRangeLabel(dayStart, dayEnd))
        root.put("exportTime", TIME_FMT.format(Date()))
        root.put("totalMessages", messages.size)

        val arr = JSONArray()
        for (m in messages) {
            val obj = JSONObject()
            obj.put("sender", m.sender)
            obj.put("text", m.text)
            obj.put("timestamp", m.timestamp)
            obj.put("time", TS_FMT.format(Date(m.timestamp)))
            arr.put(obj)
        }
        root.put("messages", arr)

        return root.toString(2)
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
        return DATE_FMT.format(Date(dayStart))
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}
