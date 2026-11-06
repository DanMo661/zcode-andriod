package com.zcodeandriod.app.core.net

import com.zcodeandriod.app.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI 兼容流式聊天客户端。
 * 取消语义：OkHttp 阻塞读不响应协程取消，必须显式 call.cancel()（学 dsh-go）。
 */
class ChatClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val active = AtomicReference<Call?>(null)

    fun cancelActive() {
        active.getAndSet(null)?.cancel()
    }

    fun stream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        toolsSchema: kotlinx.serialization.json.JsonArray? = null,
    ): Flow<StreamEvent> = callbackFlow {
        // SseAssembler 的回调跑在普通 lambda 里，只能用非挂起的 trySend；
        // UNLIMITED 缓冲保证生产端（IO 线程）永不被背压卡死
        val emit: (StreamEvent) -> Unit = { trySend(it) }
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            toolsSchema?.let { put("tools", it) }
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    when {
                        m.toolCalls.isNotEmpty() -> add(buildJsonObject {
                            put("role", "assistant")
                            put("content", if (m.content.isEmpty()) kotlinx.serialization.json.JsonNull else JsonPrimitive(m.content))
                            put("tool_calls", buildJsonArray {
                                m.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", tc.name)
                                            put("arguments", tc.arguments)
                                        })
                                    })
                                }
                            })
                        })
                        m.role == "tool" -> add(buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", m.toolCallId.orEmpty())
                            put("content", m.content)
                        })
                        else -> add(buildJsonObject {
                            put("role", m.role)
                            if (m.imageBase64 != null) {
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", m.content)
                                    })
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            put("url", "data:image/jpeg;base64," + m.imageBase64)
                                        })
                                    })
                                })
                            } else {
                                put("content", m.content)
                            }
                        })
                    }
                }
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = http.newCall(request)
        active.set(call)

        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = runCatching { response.body?.string() }.getOrNull()
                        val detail = err?.take(300) ?: "HTTP ${response.code}"
                        trySend(StreamEvent.Failed("HTTP ${response.code}: $detail"))
                        return@use
                    }
                    val source = response.body?.source() ?: run {
                        trySend(StreamEvent.Failed("空响应体"))
                        return@use
                    }
                    // 假流式探测：部分供应商无视 stream=true 返回整块 JSON
                    val first = source.peek().readUtf8(1)
                    val assembler = SseAssembler(emit)
                    if (first == "{") {
                        val whole = json.parseToJsonElement(source.readUtf8()).jsonObject
                        val content = (((whole["choices"] as? kotlinx.serialization.json.JsonArray)
                            ?.firstOrNull()?.jsonObject?.get("message") as? JsonObject)
                            ?.get("content") as? JsonPrimitive)?.contentOrNull
                        assembler.emitWhole(content ?: "")
                        if (!assembler.closeAtEof()) {
                            trySend(StreamEvent.Failed("供应商返回非流式 JSON 但结构不符合预期"))
                        }
                    } else {
                        while (isActive && !assembler.isFinished) {
                            val line = source.readUtf8Line() ?: break
                            assembler.feedLine(line)
                        }
                        if (!assembler.closeAtEof()) {
                            trySend(StreamEvent.Failed("Incomplete provider stream"))
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                trySend(
                    if (call.isCanceled()) StreamEvent.Failed("已取消")
                    else StreamEvent.Failed("网络错误: ${e.message ?: e.javaClass.simpleName}"),
                )
            } catch (e: Exception) {
                trySend(StreamEvent.Failed("流异常: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                active.compareAndSet(call, null)
                close()
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }.buffer(Channel.UNLIMITED)
}
