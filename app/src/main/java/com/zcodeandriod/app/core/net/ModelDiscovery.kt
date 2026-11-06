package com.zcodeandriod.app.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** GET /models 模型发现：兼容 OpenAI data[].id、MiniMax models[].model_name、裸字符串数组 */
class ModelDiscovery(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.trimEnd('/') + "/models"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body)
                    fun id(e: kotlinx.serialization.json.JsonElement): String? =
                        (e as? kotlinx.serialization.json.JsonObject)?.get("id")
                            ?.let { (it as JsonPrimitive).contentOrNull }
                    val ids = when {
                        root is kotlinx.serialization.json.JsonObject && root["data"] is kotlinx.serialization.json.JsonArray ->
                            (root["data"] as kotlinx.serialization.json.JsonArray).mapNotNull(::id)
                        root is kotlinx.serialization.json.JsonObject && root["models"] is kotlinx.serialization.json.JsonArray ->
                            (root["models"] as kotlinx.serialization.json.JsonArray).mapNotNull { e ->
                                (e as? kotlinx.serialization.json.JsonObject)?.get("model_name")
                                    ?.let { (it as JsonPrimitive).contentOrNull }
                            }
                        root is kotlinx.serialization.json.JsonArray ->
                            root.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        else -> emptyList()
                    }
                    ids.filter { it.isNotBlank() }.distinct()
                }
            }
        }
}
