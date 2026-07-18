package com.example.wechatstats.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

object ImportUtils {

    private const val TYPE_GROUP = "group"
    private const val TYPE_MEMBER = "member"
    private const val TYPE_MESSAGES = "messages"

    fun parseImportForGroupList(
        context: Context,
        uri: Uri
    ): Result<ImportData> {
        val root = parseJson(context, uri) ?: return Result.failure(ImportException("无法读取文件"))
        if (root.isFailure) return Result.failure(root.exceptionOrNull()!!)
        val json = root.getOrThrow()

        val fileType = json.optString("type", "")
        if (fileType != TYPE_GROUP && fileType != TYPE_MESSAGES) {
            return Result.failure(ImportException("导入文件类型不匹配：请在「群聊统计」界面导入"))
        }

        return buildRecords(json)
    }

    fun parseImportForMember(
        context: Context,
        uri: Uri,
        expectedGroupName: String
    ): Result<ImportData> {
        val root = parseJson(context, uri) ?: return Result.failure(ImportException("无法读取文件"))
        if (root.isFailure) return Result.failure(root.exceptionOrNull()!!)
        val json = root.getOrThrow()

        val fileType = json.optString("type", "")
        if (fileType != TYPE_MEMBER && fileType != TYPE_MESSAGES) {
            return Result.failure(ImportException("导入文件类型不匹配：请在「成员统计」界面导入"))
        }

        val fileGroupName = json.optString("groupName", "")
        if (fileGroupName != expectedGroupName) {
            return Result.failure(ImportException("群名不匹配：文件来自「$fileGroupName」，当前群为「$expectedGroupName」"))
        }

        return buildRecords(json)
    }

    fun parseImportForMessage(
        context: Context,
        uri: Uri,
        expectedGroupName: String,
        expectedSender: String
    ): Result<ImportData> {
        val root = parseJson(context, uri) ?: return Result.failure(ImportException("无法读取文件"))
        if (root.isFailure) return Result.failure(root.exceptionOrNull()!!)
        val json = root.getOrThrow()

        val fileType = json.optString("type", "")
        if (fileType != TYPE_MESSAGES) {
            return Result.failure(ImportException("导入文件类型不匹配：请在「消息明细」界面导入"))
        }

        val fileGroupName = json.optString("groupName", "")
        if (fileGroupName != expectedGroupName) {
            return Result.failure(ImportException("群名不匹配：文件来自「$fileGroupName」，当前群为「$expectedGroupName」"))
        }

        val fileSender = json.optString("sender", "")
        if (fileSender != expectedSender) {
            return Result.failure(ImportException("发言人不匹配：文件来自「$fileSender」，当前为「$expectedSender」"))
        }

        return buildRecords(json)
    }

    private fun parseJson(context: Context, uri: Uri): Result<JSONObject>? {
        val jsonText = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return null
        } catch (e: Exception) {
            return Result.failure(ImportException("读取文件失败: ${e.message}"))
        }

        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return Result.failure(ImportException("JSON 格式错误"))
        }

        // 兼容旧版本导出文件（无 type 字段）
        if (!root.has("type")) {
            val hasSender = root.has("sender") && root.optString("sender", "").isNotEmpty()
            root.put("type", if (hasSender) TYPE_MESSAGES else TYPE_GROUP)
        }

        return Result.success(root)
    }

    private fun buildRecords(root: JSONObject): Result<ImportData> {
        val rootGroupName = root.optString("groupName", "")
        val fileSender = root.optString("sender", "")
        val messages = root.optJSONArray("messages") ?: JSONArray()
        val len = messages.length()
        val records = ArrayList<MessageRecord>(len)

        // 判断是新格式（短字段名）还是旧格式
        val isOldFmt = len > 0 && isOldFormat(messages.getJSONObject(0))

        for (i in 0 until len) {
            val msg = messages.getJSONObject(i)
            val sender = if (isOldFmt) {
                msg.optString("sender", fileSender)
            } else {
                msg.optString("s", fileSender)
            }
            val text = if (isOldFmt) {
                msg.optString("text", "")
            } else {
                msg.optString("t", "")
            }
            val timestamp = if (isOldFmt) {
                msg.optLong("timestamp", 0L)
            } else {
                msg.optLong("ts", 0L)
            }
            // 根层有 groupName 则继承，否则从消息自身读取（旧格式兼容）
            val groupName = if (rootGroupName.isNotEmpty()) rootGroupName
            else if (isOldFmt) msg.optString("groupName", "")
            else rootGroupName
            val notificationKey = computeKey(groupName, sender, text, timestamp / 1000)
            records.add(
                MessageRecord(
                    notificationKey = notificationKey,
                    groupName = groupName,
                    sender = sender,
                    text = text,
                    timestamp = timestamp
                )
            )
        }

        return Result.success(ImportData(records, rootGroupName, fileSender))
    }

    private fun isOldFormat(msg: JSONObject): Boolean {
        return msg.has("sender") || msg.has("groupName")
    }

    private fun computeKey(groupName: String, sender: String, text: String, postTimeSeconds: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(groupName.toByteArray(Charsets.UTF_8))
        md.update('|'.code.toByte())
        md.update(sender.toByteArray(Charsets.UTF_8))
        md.update('|'.code.toByte())
        md.update(text.toByteArray(Charsets.UTF_8))
        md.update('|'.code.toByte())
        for (b in postTimeSeconds.toString().toByteArray(Charsets.UTF_8)) md.update(b)
        val bytes = md.digest()
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

data class ImportData(
    val records: List<MessageRecord>,
    val groupName: String,
    val sender: String
)

class ImportException(message: String) : Exception(message)