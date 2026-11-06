package com.zcodeandriod.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 供应商配置存储：全量（含 apiKey）进 EncryptedSharedPreferences。
 * Keystore 不可用时置 [available]=false 拒绝服务，绝不降级明文（学 dsh-go 的做法）。
 * 当前模型选择等非敏感项走普通 prefs。
 */
class ProviderStore(context: Context) {

    val available: Boolean

    private val secure: SharedPreferences?
    private val plain: SharedPreferences =
        context.getSharedPreferences("zcodea_provider", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    init {
        val prefs: SharedPreferences? = try {
            val master = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "zcodea_secure",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            null
        }
        secure = prefs
        available = prefs != null
    }

    fun load(): List<Provider> {
        val s = secure ?: return emptyList()
        val raw = s.getString(KEY_PROVIDERS, null) ?: return seedDefaults()
        return runCatching { json.decodeFromString<List<Provider>>(raw) }.getOrDefault(emptyList())
    }

    fun save(providers: List<Provider>) {
        val s = secure ?: error("secure storage unavailable")
        s.edit().putString(KEY_PROVIDERS, json.encodeToString(providers)).apply()
    }

    /** 修改敏感字段后整体重写；id 不变则覆盖 */
    fun upsert(provider: Provider) {
        val list = load().filterNot { it.id == provider.id } + provider
        save(list)
    }

    fun remove(id: String) {
        save(load().filterNot { it.id == id })
        if (currentProviderId() == id) setCurrentProvider("")
    }

    fun currentProviderId(): String = plain.getString(KEY_CURRENT, "") ?: ""

    fun setCurrentProvider(id: String) {
        plain.edit().putString(KEY_CURRENT, id).apply()
    }

    fun currentModel(): String = plain.getString(KEY_MODEL, "") ?: ""

    fun setCurrentModel(model: String) {
        plain.edit().putString(KEY_MODEL, model).apply()
    }

    /** 取当前生效的供应商与模型；未配置返回 null */
    fun effective(): Pair<Provider, String>? {
        val id = currentProviderId()
        val list = load()
        val p = list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: return null
        val model = currentModel().ifBlank { p.defaultModel.ifBlank { p.models.firstOrNull() ?: "" } }
        if (model.isBlank()) return null
        return p to model
    }

    /** 首次使用时预置一个智谱开放平台条目（key 留空，由用户填写） */
    private fun seedDefaults(): List<Provider> {
        val seed = listOf(
            Provider(
                id = "bigmodel",
                name = "智谱开放平台",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                apiKey = "",
                models = listOf("glm-4.6", "glm-4.5", "glm-4.5-air"),
                defaultModel = "glm-4.6",
            ),
        )
        save(seed)
        return seed
    }

    private companion object {
        const val KEY_PROVIDERS = "providers"
        const val KEY_CURRENT = "current_provider_id"
        const val KEY_MODEL = "current_model"
    }
}
