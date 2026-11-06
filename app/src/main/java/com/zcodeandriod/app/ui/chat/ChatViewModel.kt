package com.zcodeandriod.app.ui.chat

import com.zcodeandriod.app.core.AgentException
import com.zcodeandriod.app.core.AgentLoop
import com.zcodeandriod.app.core.Graph
import com.zcodeandriod.app.data.ChatMessage
import com.zcodeandriod.app.data.ChatSession
import com.zcodeandriod.app.data.SessionMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val streamingText: String = "",
    val streamingTool: String? = null,
    val error: String? = null,
    val sessionTitle: String = "新对话",
)

/**
 * 对话状态与执行：自持 scope（Graph.appScope），
 * 不用 rememberCoroutineScope——重组会杀掉生成中的任务（dsh-go 的教训）。
 * 存储在 data/、网络在 core/net/、工具循环在 core/AgentLoop，这里只做编排。
 */
class ChatViewModel(private val scope: CoroutineScope) {

    private val store = Graph.sessionStore
    private val providers = Graph.providerStore
    private val loop = Graph.agentLoop

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var session: ChatSession? = null
    private var runJob: Job? = null

    init {
        store.list().firstOrNull()?.let { meta ->
            session = store.load(meta.id)
            _state.update {
                it.copy(messages = session?.messages ?: emptyList(), sessionTitle = meta.title)
            }
        }
    }

    fun sessions(): List<SessionMeta> = store.list()

    fun newSession() {
        stop()
        session = null
        _state.update { ChatUiState() }
    }

    fun switchTo(id: String) {
        stop()
        val s = store.load(id) ?: return
        session = s
        _state.update { it.copy(messages = s.messages, sessionTitle = s.title, error = null) }
    }

    fun deleteSession(id: String) {
        store.delete(id)
        if (session?.id == id) newSession()
    }

    fun stop() {
        Graph.chatClient.cancelActive()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.streaming) return
        val effective = providers.effective()
        if (effective == null) {
            _state.update { it.copy(error = "请先在「设置」里配置 API 供应商与模型") }
            return
        }
        val (provider, model) = effective

        val s = session ?: store.newSession().also { session = it }
        val withUser = s.copy(
            messages = s.messages + ChatMessage("user", trimmed, System.currentTimeMillis()),
            updatedAt = System.currentTimeMillis(),
        )
        val titled = store.autoTitle(withUser)
        session = titled
        store.save(titled)
        _state.update {
            it.copy(
                messages = titled.messages,
                sessionTitle = titled.title,
                streaming = true,
                streamingText = "",
                streamingTool = null,
                error = null,
            )
        }

        val history = buildList {
            add(ChatMessage("system", SYSTEM_PROMPT))
            addAll(titled.messages)
        }
        val tools = Graph.fileTools.all() + Graph.httpTool + Graph.phoneTools.all()

        runJob = scope.launch {
            val sb = StringBuilder()
            try {
                loop.run(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    model = model,
                    history = history,
                    tools = tools,
                    callbacks = object : AgentLoop.Callbacks {
                        override fun onDelta(delta: String) {
                            sb.append(delta)
                            _state.update { it.copy(streamingText = sb.toString()) }
                        }

                        override fun onToolStart(name: String) {
                            _state.update { it.copy(streamingTool = "🔧 $name …") }
                        }

                        override fun onToolDone(name: String, ok: Boolean) {
                            _state.update { it.copy(streamingTool = null) }
                        }

                        override fun onRoundAppended(messages: List<ChatMessage>) {
                            val base = session ?: return
                            val updated = base.copy(
                                messages = base.messages + messages,
                                updatedAt = System.currentTimeMillis(),
                            )
                            session = updated
                            store.save(updated)
                            _state.update {
                                it.copy(
                                    messages = updated.messages,
                                    streamingText = "",
                                    streamingTool = null,
                                )
                            }
                        }
                    },
                )
            } catch (e: AgentException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "执行异常: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                _state.update {
                    it.copy(
                        streaming = false,
                        streamingText = "",
                        streamingTool = null,
                        messages = session?.messages ?: it.messages,
                    )
                }
            }
        }
    }

    private companion object {
        const val SYSTEM_PROMPT = "你是 ZcodeAndriod，一个运行在 Android 手机上的 AI 编程助手。" +
            "你可以使用工具：read_file / write_file / list_files 操作手机私有工作区（一律用相对路径），" +
            "http_get 抓取网页，phone_observe / phone_action 观察和操作手机（动作后必须重新观察确认；" +
            "支付、银行、密码界面一律拒绝操作并请用户手动完成）。回答简洁准确，默认使用中文。"
    }
}
