package com.zcodeandriod.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zcodeandriod.app.data.ChatMessage
import com.zcodeandriod.app.ui.theme.ZcodeSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var showSessions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val lastCount = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
    LaunchedEffect(lastCount, state.streamingText.length) {
        if (lastCount > 0) {
            runCatching { listState.scrollToItem(lastCount - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.sessionTitle, maxLines = 1) },
                actions = {
                    IconButton(onClick = { viewModel.newSession() }) {
                        Icon(Icons.Filled.Add, contentDescription = "新对话")
                    }
                    IconButton(onClick = { showSessions = true }) {
                        Icon(Icons.Filled.List, contentDescription = "会话列表")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(ZcodeSpace.lg),
                verticalArrangement = Arrangement.spacedBy(ZcodeSpace.md),
            ) {
                items(state.messages) { m -> MessageBubble(m) }
                if (state.streaming) {
                    if (state.streamingText.isNotEmpty()) {
                        item { Bubble(text = state.streamingText, isUser = false) }
                    }
                    state.streamingTool?.let { tool ->
                        item {
                            Text(
                                tool,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZcodeSpace.lg, vertical = ZcodeSpace.xs),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZcodeSpace.lg, vertical = ZcodeSpace.md),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(ZcodeSpace.radius),
                )
                IconButton(
                    onClick = {
                        if (state.streaming) {
                            viewModel.stop()
                        } else {
                            viewModel.send(input)
                            input = ""
                        }
                    },
                    modifier = Modifier.padding(start = ZcodeSpace.sm),
                ) {
                    Icon(
                        imageVector = if (state.streaming) Icons.Filled.Close else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (state.streaming) "停止" else "发送",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showSessions) {
        ModalBottomSheet(onDismissRequest = { showSessions = false }) {
            val metas = remember(state.sessionTitle) { viewModel.sessions() }
            Text(
                "会话列表",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = ZcodeSpace.lg, vertical = ZcodeSpace.sm),
            )
            if (metas.isEmpty()) {
                Text(
                    "还没有会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(ZcodeSpace.lg),
                )
            }
            metas.forEach { meta ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZcodeSpace.lg, vertical = ZcodeSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable { viewModel.switchTo(meta.id); showSessions = false },
                    ) {
                        Column {
                            Text(meta.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                "${meta.messageCount} 条消息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.deleteSession(meta.id) }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.Close,
                            contentDescription = "删除会话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = ZcodeSpace.lg))
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage) {
    Box(Modifier.fillMaxWidth()) {
        when {
            m.role == "tool" -> ToolResultCard(m, Modifier.align(Alignment.CenterStart))
            m.toolCalls.isNotEmpty() -> Column(Modifier.align(Alignment.CenterStart)) {
                m.toolCalls.forEach { tc ->
                    Text(
                        "🔧 ${tc.name}(${tc.arguments.take(80)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> Bubble(
                text = m.content,
                isUser = m.role == "user",
                modifier = Modifier.align(if (m.role == "user") Alignment.CenterEnd else Alignment.CenterStart),
            )
        }
    }
}

@Composable
private fun ToolResultCard(m: ChatMessage, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(ZcodeSpace.radius),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = ZcodeSpace.lg, vertical = ZcodeSpace.md)) {
            Text(
                if (m.content.startsWith("error")) "⚠ 工具结果" else "✓ 工具结果",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                m.content.take(400),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 6,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Bubble(text: String, isUser: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 320.dp)
            .background(
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(ZcodeSpace.radius),
            )
            .padding(horizontal = ZcodeSpace.lg, vertical = ZcodeSpace.md),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
    }
}
