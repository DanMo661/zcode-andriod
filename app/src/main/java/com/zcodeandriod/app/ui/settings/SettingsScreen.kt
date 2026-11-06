package com.zcodeandriod.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.zcodeandriod.app.core.Graph
import com.zcodeandriod.app.data.Provider
import com.zcodeandriod.app.ui.components.SectionPanel
import com.zcodeandriod.app.ui.theme.ZcodeSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 供应商与模型配置。协议一期只做 OpenAI 兼容（Anthropic 分支 dsh-go 也是半成品）。
 * apiKey 经 EncryptedSharedPreferences 落盘；Keystore 不可用则功能禁用，不降级明文。
 */
@Composable
fun SettingsScreen() {
    val store = Graph.providerStore
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var currentPid by remember { mutableStateOf("") }
    var currentModel by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Provider?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var secureBroken by remember { mutableStateOf(false) }

    suspend fun refresh() {
        secureBroken = !store.available
        val loaded = withContext(Dispatchers.IO) {
            val list = if (store.available) store.load() else emptyList()
            Triple(list, store.currentProviderId(), store.currentModel())
        }
        providers = loaded.first
        currentPid = loaded.second
        currentModel = loaded.third
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZcodeSpace.lg),
        verticalArrangement = Arrangement.spacedBy(ZcodeSpace.lg),
    ) {
        if (secureBroken) {
            SectionPanel("安全存储不可用") {
                Text(
                    "系统 Keystore 初始化失败，无法安全保存 API Key，配置功能已停用。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            SectionPanel("当前模型") {
                if (providers.isEmpty()) {
                    Text("还没有供应商，先添加一个。", style = MaterialTheme.typography.bodyMedium)
                }
                providers.forEach { p ->
                    FilterChip(
                        selected = p.id == currentPid,
                        onClick = {
                            store.setCurrentProvider(p.id)
                            store.setCurrentModel(p.defaultModel.ifBlank { p.models.firstOrNull() ?: "" })
                            currentPid = p.id
                            currentModel = p.defaultModel.ifBlank { p.models.firstOrNull() ?: "" }
                        },
                        label = { Text(p.name) },
                        modifier = Modifier.padding(end = ZcodeSpace.sm, bottom = ZcodeSpace.sm),
                    )
                }
                val cur = providers.firstOrNull { it.id == currentPid }
                if (cur != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZcodeSpace.sm)) {
                        cur.models.forEach { m ->
                            FilterChip(
                                selected = m == currentModel,
                                onClick = {
                                    store.setCurrentModel(m)
                                    currentModel = m
                                },
                                label = { Text(m) },
                            )
                        }
                    }
                }
            }

            SectionPanel("供应商") {
                providers.forEach { p ->
                    Surface(
                        onClick = { editing = p; editorOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = ZcodeSpace.sm),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(ZcodeSpace.md)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                p.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = {
                    editing = null
                    editorOpen = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  添加供应商")
                }
            }
        }
    }

    if (editorOpen) {
        ProviderEditorDialog(
            initial = editing,
            onDismiss = { editorOpen = false },
            onSaved = {
                editorOpen = false
                Graph.appScope.launch { refresh() }
            },
        )
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: Provider?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val store = Graph.providerStore
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://open.bigmodel.cn/api/paas/v4") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var modelsText by remember {
        mutableStateOf(initial?.models?.joinToString(", ") ?: "glm-4.6, glm-4.5, glm-4.5-air")
    }
    var discovering by remember { mutableStateOf(false) }
    var discoverMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加供应商" else "编辑供应商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZcodeSpace.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（OpenAI 兼容）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = modelsText,
                    onValueChange = { modelsText = it },
                    label = { Text("模型（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ZcodeSpace.md)) {
                    OutlinedButton(
                        enabled = !discovering && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                        onClick = {
                            discovering = true
                            discoverMsg = null
                            Graph.appScope.launch {
                                val result = Graph.modelDiscovery.discover(baseUrl.trim(), apiKey.trim())
                                discovering = false
                                result.fold(
                                    onSuccess = { ids ->
                                        if (ids.isNotEmpty()) {
                                            modelsText = ids.joinToString(", ")
                                            discoverMsg = "拉取到 ${ids.size} 个模型"
                                        } else {
                                            discoverMsg = "返回列表为空"
                                        }
                                    },
                                    onFailure = { discoverMsg = "拉取失败：${it.message}" },
                                )
                            }
                        },
                    ) { Text(if (discovering) "拉取中…" else "拉取模型列表") }
                }
                discoverMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                onClick = {
                    val models = modelsText.split(",", "，")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    val p = Provider(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        baseUrl = baseUrl.trim().trimEnd('/'),
                        apiKey = apiKey.trim(),
                        models = models,
                        defaultModel = initial?.defaultModel?.ifBlank { models.firstOrNull() ?: "" }
                            ?: models.firstOrNull().orEmpty(),
                    )
                    Graph.appScope.launch {
                        withContext(Dispatchers.IO) {
                            store.upsert(p)
                            if (store.currentProviderId().isBlank() ||
                                store.currentProviderId() == p.id
                            ) {
                                store.setCurrentProvider(p.id)
                                store.setCurrentModel(p.defaultModel)
                            }
                        }
                        onSaved()
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            if (initial != null) {
                TextButton(
                    onClick = {
                        Graph.appScope.launch {
                            withContext(Dispatchers.IO) { store.remove(initial.id) }
                            onSaved()
                        }
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
