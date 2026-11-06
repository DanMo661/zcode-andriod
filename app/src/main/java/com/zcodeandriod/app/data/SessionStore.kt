package com.zcodeandriod.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 会话存储：每会话一个 JSON 文件 + index.json 元数据索引。
 * 不学 dsh-go 把全部会话塞一条 SharedPreferences（1MB Binder 上限风险）。
 * 写入均为 tmp+rename 原子替换。
 */
class SessionStore(context: Context) {

    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun list(): List<SessionMeta> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<SessionMeta>>(indexFile.readText())
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    fun load(id: String): ChatSession? {
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<ChatSession>(f.readText()) }.getOrNull()
    }

    fun save(session: ChatSession) {
        // 截图帧只驻内存：落盘前剥离
        val clean = session.copy(
            messages = session.messages.map { if (it.imageBase64 != null) it.copy(imageBase64 = null) else it },
        )
        val tmp = File(dir, "${session.id}.json.tmp")
        tmp.writeText(json.encodeToString(clean))
        val dst = File(dir, "${session.id}.json")
        if (!tmp.renameTo(dst)) {
            dst.delete()
            check(tmp.renameTo(dst)) { "session rename failed" }
        }
        val metas = list().filterNot { it.id == session.id } + SessionMeta(
            id = session.id,
            title = session.title,
            updatedAt = session.updatedAt,
            messageCount = session.messages.size,
        )
        writeIndex(metas)
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        writeIndex(list().filterNot { it.id == id })
    }

    fun newSession(title: String = "新对话"): ChatSession {
        val now = System.currentTimeMillis()
        return ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** 用首条用户消息前 18 字做标题 */
    fun autoTitle(session: ChatSession): ChatSession {
        if (session.title != "新对话") return session
        val first = session.messages.firstOrNull { it.role == "user" }?.content ?: return session
        val t = first.replace("\n", " ").take(18)
        return session.copy(title = t.ifBlank { session.title })
    }

    private fun writeIndex(metas: List<SessionMeta>) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json.encodeToString(metas))
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            check(tmp.renameTo(indexFile)) { "index rename failed" }
        }
    }
}
