package com.zcodeandriod.app.core

import android.content.Context
import com.zcodeandriod.app.core.net.ChatClient
import com.zcodeandriod.app.core.net.ModelDiscovery
import com.zcodeandriod.app.data.ProviderStore
import com.zcodeandriod.app.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 轻量服务定位器：只做装配，不放业务逻辑。
 * 单例各自职责单一，禁止把状态堆进这里（戒 dsh-go 的 God Object）。
 */
object Graph {
    private lateinit var appContext: Context

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // 长流式响应不设整体超时
            .callTimeout(0, TimeUnit.SECONDS)
            // 3xx 重定向会重放付费 POST；503+Retry-After 会被 OkHttp 自动重试，都关掉
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    val providerStore: ProviderStore by lazy { ProviderStore(appContext) }
    val sessionStore: SessionStore by lazy { SessionStore(appContext) }
    val chatClient: ChatClient by lazy { ChatClient(okHttp) }
    val modelDiscovery: ModelDiscovery by lazy { ModelDiscovery(okHttp) }
    val agentLoop: AgentLoop by lazy {
        AgentLoop(chatClient) { com.zcodeandriod.app.agent.ScreenCaptureService.lastFrameBase64 }
    }
    val fileTools: com.zcodeandriod.app.tools.FileTools by lazy {
        com.zcodeandriod.app.tools.FileTools(
            java.io.File(appContext.filesDir, "workspace").apply { mkdirs() },
        )
    }
    val httpTool: com.zcodeandriod.app.tools.HttpTool by lazy { com.zcodeandriod.app.tools.HttpTool(okHttp) }
    val phoneTools: com.zcodeandriod.app.tools.PhoneTools by lazy {
        com.zcodeandriod.app.tools.PhoneTools { com.zcodeandriod.app.agent.ZcodeAAccessibilityService.instance }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
