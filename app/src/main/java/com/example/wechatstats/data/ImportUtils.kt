package com.example.wechatstats.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

data class ImportResult(
    val total: Int,
    val inserted: Int,
    val skipped: Int
)

object ImportUtils {

    private const val TYPE_GROUP = "group"
    private const val TYPE_MEMBER = "member"
    private const val TYPE_MESSAGES = "messages"

    /**
     * 从 URI 读取并解析导入文件。
     * @param expectedType 期望的导入类型（group/member/messages）
     * @param expectedGroupName 期望的群名（member/messages 需要匹配）
     * @param expectedSender 期望的发言人（messages 需要匹配）
     * @return 解析后的记录列表 + 元数据，或错误信息
     */
    fun parseImport(
        context: Context,
        uri: Uri,
        expectedType: String,
        expectedGroupName: String? = null,
        expectedSender: String? = null
    ): Result<ImportData> {
        val jsonText = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return Result.failure(ImportException("无法读取文件"))
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

        val fileType = root.getString("type")
        if (fileType != expectedType) {
            val typeName = when (fileType) {
                TYPE_GROUP -> "群聊统计"
                TYPE_MEMBER -> "成员统计"
                TYPE_MESSAGES -> "消息明细"
                else -> "未知"
            }
            val expectedName = when (expectedType) {
                TYPE_GROUP -> "群聊统计"
                TYPE_MEMBER -> "成员统计"
                TYPE_MESSAGES -> "消息明细"
                else -> "未知"
            }
            return Result.failure(ImportException("导入文件类型不匹配：文件是「$typeName」，请在「$expectedName」界面导入"))
        }

        val fileGroupName = root.optString("groupName", "")
        val fileSender = root.optString("sender", "")

        // 校验群名匹配
        if (expectedGroupName != null && fileGroupName != expectedGroupName) {
            return Result.failure(ImportException("群名不匹配：文件来自「$fileGroupName」，当前群为「$expectedGroupName」"))
        }

        // 校验发言人匹配
        if (expectedSender != null && fileSender != expectedSender) {
            return Result.failure(ImportException("发言人不匹配：文件来自「$fileSender」，当前为「$expectedSender」"))
        }

        val messages = root.optJSONArray("messages") ?: JSONArray()
        val records = mutableListOf<MessageRecord>()

        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val sender = msg.optString("sender", fileSender)
            val text = msg.optString("text", "")
            val timestamp = msg.optLong("timestamp", 0L)
            val notificationKey = computeKey(fileGroupName, sender, text, timestamp / 1000)
            records.add(
                MessageRecord(
                    notificationKey = notificationKey,
                    groupName = fileGroupName,
                    sender = sender,
                    text = text,
                    timestamp = timestamp
                )
            )
        }

        return Result.success(ImportData(records, fileGroupName, fileSender))
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