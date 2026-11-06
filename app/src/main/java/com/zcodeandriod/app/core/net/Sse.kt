package com.zcodeandriod.app.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 流式事件：N1 只用 Delta/Done/Failed；ToolCallDelta 为 N2 工具循环预留 */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argsDelta: String,
    ) : StreamEvent

    data class Done(
        val finishReason: String? = null,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
    ) : StreamEvent

    data class Failed(val message: String) : StreamEvent
}

/**
 * 纯 SSE/JSON 分块装配器（无 HTTP 依赖，可单测）。
 * 边界处理学 dsh-go：[DONE] 结束、EOF 时凭 finish_reason 收口、
 * usage 只认快照不重复累加、tool_calls 按 index 分片交给上层。
 */
class SseAssembler(
    private val onEvent: (StreamEvent) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var finished = false
    private var finishReason: String? = null
    private var promptTokens = 0
    private var completionTokens = 0
    private var usageSeen = false

    val isFinished: Boolean get() = finished

    /** @return 消费完这一行后流是否应结束 */
    fun feedLine(line: String): Boolean {
        if (finished) return true
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("data:")) return false
        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") {
            finish()
            return true
        }
        parseChunk(data)
        return finished
    }

    /** 假流式：供应商无视 stream=true 返回整块 JSON，抽 message.content 一次性发出 */
    fun emitWhole(content: String) {
        if (finished || content.isEmpty()) return
        onEvent(StreamEvent.Delta(content))
    }

    /** 流正常 EOF（无 [DONE]）时调用；凭 finish_reason 收口，否则由调用方按不完整流报错 */
    fun closeAtEof(): Boolean {
        if (finished) return true
        if (finishReason != null) {
            finish()
            return true
        }
        return false
    }

    private fun finish() {
        if (finished) return
        finished = true
        onEvent(StreamEvent.Done(finishReason, promptTokens, completionTokens))
    }

    private fun parseChunk(data: String) {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        (obj["choices"] as? JsonArray)?.firstOrNull()?.let { c ->
            val choice = c.jsonObject
            (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { finishReason = it }
            (choice["delta"] as? JsonObject)?.let { d ->
                (d["content"] as? JsonPrimitive)?.contentOrNull?.let {
                    if (it.isNotEmpty()) onEvent(StreamEvent.Delta(it))
                }
                (d["tool_calls"] as? JsonArray)?.forEach { tc ->
                    val t = tc.jsonObject
                    val fn = t["function"] as? JsonObject
                    onEvent(
                        StreamEvent.ToolCallDelta(
                            index = t["index"]?.let { (it as JsonPrimitive).intOrNull } ?: 0,
                            id = (t["id"] as? JsonPrimitive)?.contentOrNull,
                            name = fn?.get("name")?.let { (it as JsonPrimitive).contentOrNull },
                            argsDelta = fn?.get("arguments")?.let { (it as JsonPrimitive).contentOrNull } ?: "",
                        ),
                    )
                }
            }
        }
        if (!usageSeen) {
            (obj["usage"] as? JsonObject)?.let { u ->
                usageSeen = true
                promptTokens = u.intOf("prompt_tokens")
                completionTokens = u.intOf("completion_tokens")
            }
        }
    }

    private fun JsonObject.intOf(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0
}
