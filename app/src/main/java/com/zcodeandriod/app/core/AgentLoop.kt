package com.zcodeandriod.app.core

import com.zcodeandriod.app.core.net.ChatClient
import com.zcodeandriod.app.core.net.StreamEvent
import com.zcodeandriod.app.data.ChatMessage
import com.zcodeandriod.app.data.ToolCall
import com.zcodeandriod.app.tools.LocalTool
import com.zcodeandriod.app.tools.truncateForTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AgentException(message: String) : Exception(message)

/**
 * 工具循环：每轮 流式请求→装配 assistant 消息（文本+tool_calls）→
 * 执行工具→回填 tool 结果→进下一轮；模型不再发起调用即收口。
 * 边界学 dsh-go：40 轮上限、单轮 ≤12 个调用、闭式 allowlist、每个 tool_call 必须有回执。
 */
class AgentLoop(
    private val client: ChatClient,
    /** phone_capture 成功后取回截图 Base64 的钩子（仅 phone_capture 轮次被调用） */
    private val captureHook: () -> String? = { null },
) {

    private val json = Json { ignoreUnknownKeys = true }

    interface Callbacks {
        /** 流式文本增量 */
        fun onDelta(text: String) {}
        /** 工具开始执行 */
        fun onToolStart(name: String) {}
        /** 工具执行结束 */
        fun onToolDone(name: String, ok: Boolean) {}
        /** 每轮结束：把新增消息交给调用方（持久化/上屏） */
        fun onRoundAppended(messages: List<ChatMessage>) {}
    }

    suspend fun run(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        tools: List<LocalTool>,
        callbacks: Callbacks,
    ) {
        val registry = tools.associateBy { it.name }
        val schema = buildJsonArray {
            tools.forEach { t ->
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", json.parseToJsonElement(t.parametersJson))
                    })
                })
            }
        }

        val appended = mutableListOf<ChatMessage>()
        val now = System.currentTimeMillis()

        for (round in 0 until MAX_ROUNDS) {
            val sb = StringBuilder()
            val pending = LinkedHashMap<Int, PendingCall>()
            var failed: String? = null
            var finishReason: String? = null

            client.stream(baseUrl, apiKey, model, history + appended, schema)
                .collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            sb.append(ev.text)
                            callbacks.onDelta(ev.text)
                        }
                        is StreamEvent.ToolCallDelta -> {
                            val p = pending.getOrPut(ev.index) { PendingCall(ev.index) }
                            ev.id?.let { p.id = it }
                            ev.name?.let { p.name = it }
                            p.args.append(ev.argsDelta)
                        }
                        is StreamEvent.Done -> finishReason = ev.finishReason
                        is StreamEvent.Failed -> failed = ev.message
                    }
                }
            failed?.let { throw AgentException(it) }

            val text = sb.toString()
            val toolCalls = pending.toSortedMap().values.mapIndexed { i, p ->
                ToolCall(
                    id = p.id.ifBlank { "call_${round}_$i" },
                    name = p.name.orEmpty(),
                    arguments = p.args.toString().ifBlank { "{}" },
                )
            }

            if (toolCalls.isEmpty()) {
                val msg = ChatMessage("assistant", text, System.currentTimeMillis())
                appended.add(msg)
                callbacks.onRoundAppended(listOf(msg))
                return
            }

            appended.add(
                ChatMessage("assistant", text, System.currentTimeMillis(), toolCalls = toolCalls),
            )

            val toolMessages = toolCalls.take(MAX_CALLS_PER_ROUND).map { tc ->
                callbacks.onToolStart(tc.name)
                val result = runTool(registry, tc)
                callbacks.onToolDone(tc.name, ok = !result.startsWith("error"))
                ChatMessage(
                    role = "tool",
                    content = result.truncateForTool(),
                    at = System.currentTimeMillis(),
                    toolCallId = tc.id,
                )
            } + toolCalls.drop(MAX_CALLS_PER_ROUND).map { tc ->
                ChatMessage(
                    role = "tool",
                    content = "error: 单轮工具调用超过上限",
                    at = System.currentTimeMillis(),
                    toolCallId = tc.id,
                )
            }
            appended.addAll(toolMessages)
            callbacks.onRoundAppended(toolMessages)

            // 截屏帧以临时 user 消息图片块带给模型（本轮有效，不落盘）
            if (toolCalls.any { it.name == "phone_capture" }) {
                captureHook()?.let { b64 ->
                    val imgMsg = ChatMessage(
                        role = "user",
                        content = "以下是本轮 phone_capture 的屏幕截图（临时，仅本轮有效）",
                        at = System.currentTimeMillis(),
                        imageBase64 = b64,
                    )
                    appended.add(imgMsg)
                    callbacks.onRoundAppended(listOf(imgMsg))
                }
            }
        }
        throw AgentException("已达 $MAX_ROUNDS 轮工具循环上限仍未完成")
    }

    private suspend fun runTool(
        registry: Map<String, LocalTool>,
        tc: ToolCall,
    ): String = try {
        val tool = registry[tc.name]
            ?: return "error: 未知工具 ${tc.name}"
        val args = runCatching {
            json.parseToJsonElement(tc.arguments.ifBlank { "{}" }) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        tool.execute(args)
    } catch (e: SecurityException) {
        "error: ${e.message}"
    } catch (e: Exception) {
        "error: ${e.message ?: e.javaClass.simpleName}"
    }

    private class PendingCall(val index: Int) {
        var id: String = ""
        var name: String? = null
        val args = StringBuilder()
    }

    private companion object {
        const val MAX_ROUNDS = 40
        const val MAX_CALLS_PER_ROUND = 12
    }
}
