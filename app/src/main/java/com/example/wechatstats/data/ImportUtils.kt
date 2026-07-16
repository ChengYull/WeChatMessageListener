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

    /**
     * 群列表页导入：接受 group 或 messages 类型。
     */
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

    /**
     * 成员页导入：接受 member 或 messages 类型，需群名匹配。
     */
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

    /**
     * 消息页导入：接受 messages 类型，需群名+发言人匹配。
     */
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

        if (!root.has("type")) {
            return Result.failure(ImportException("无法识别的导入文件：缺少类型标识"))
        }

        return Result.success(root)
    }

    private fun buildRecords(root: JSONObject): Result<ImportData> {
        val groupName = root.optString("groupName", "")
        val fileSender = root.optString("sender", "")
        val messages = root.optJSONArray("messages") ?: JSONArray()
        val records = mutableListOf<MessageRecord>()

        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val sender = msg.optString("sender", fileSender)
            val text = msg.optString("text", "")
            val timestamp = msg.optLong("timestamp", 0L)
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

        return Result.success(ImportData(records, groupName, fileSender))
    }

    private fun computeKey(groupName: String, sender: String, text: String, postTimeSeconds: Long): String {
        val raw = "$groupName|$sender|$text|$postTimeSeconds"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

data class ImportData(
    val records: List<MessageRecord>,
    val groupName: String,
    val sender: String
)

class ImportException(message: String) : Exception(message)