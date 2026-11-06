package com.zcodeandriod.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** http_get：仅 http(s)，15s 超时，结果截断 20k。明文 http 受 Android 网络策略限制（https 不受影响）。 */
class HttpTool(private val http: OkHttpClient) : LocalTool {

    private val shortClient = http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override val name = "http_get"
    override val description = "GET 一个 http(s) 网页/接口，返回响应正文（截断 20000 字符）"
    override val parametersJson = """{"type":"object","properties":{"url":{"type":"string","description":"完整 URL"}},"required":["url"]}"""

    override suspend fun execute(args: JsonObject): String = withContext(Dispatchers.IO) {
        val url = (args["url"] as? JsonPrimitive)?.contentOrNull
            ?: return@withContext "error: missing url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext "error: 仅支持 http(s)"
        }
        runCatching {
            shortClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                val head = "HTTP ${resp.code} ${resp.header("content-type") ?: ""}\n\n"
                head + (resp.body?.string() ?: "").truncateForTool()
            }
        }.getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
    }
}
