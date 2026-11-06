package com.zcodeandriod.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** OpenAI 兼容供应商配置（apiKey 只存 EncryptedSharedPreferences，见 ProviderStore） */
@Serializable
data class Provider(
    val id: String,
    val name: String,
    /** 形如 https://open.bigmodel.cn/api/paas/v4，不带尾斜杠 */
    val baseUrl: String,
    val apiKey: String,
    val models: List<String> = emptyList(),
    val defaultModel: String = "",
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatMessage(
    val role: String, // user / assistant / system / tool
    val content: String,
    val at: Long = 0,
    /** assistant 发起的工具调用 */
    val toolCalls: List<ToolCall> = emptyList(),
    /** role=tool 时回填对应调用的 id */
    val toolCallId: String? = null,
    /** 临时截图（Base64 JPEG），仅驻内存，落盘前剥离 */
    @Transient
    val imageBase64: String? = null,
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SessionMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
)
