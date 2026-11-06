package com.zcodeandriod.app.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zcodeandriod.app.R
import com.zcodeandriod.app.agent.ScreenCaptureService
import com.zcodeandriod.app.agent.ZcodeAAccessibilityService
import com.zcodeandriod.app.core.Graph
import com.zcodeandriod.app.ui.Page
import com.zcodeandriod.app.ui.components.SectionPanel
import com.zcodeandriod.app.ui.theme.ZcodeSpace

/**
 * 启动台：本项目的差异化——API / 设备授权 / 工作区状态的统一控制台。
 * 每张状态卡可点击直达对应页；吉祥物镇楼。
 */
@Composable
fun LauncherScreen(onNavigate: (Page) -> Unit) {
    val context = LocalContext.current
    // 进入本页时读取一次状态；设备页开启后回到启动台会重组刷新
    var accessibilityOn by remember { mutableStateOf(ZcodeAAccessibilityService.isEnabled(context)) }
    var captureOn by remember { mutableStateOf(ScreenCaptureService.ready()) }
    val apiReady = remember {
        val store = Graph.providerStore
        store.available && store.effective()?.first?.apiKey?.isNotBlank() == true
    }
    val sessionCount = remember { Graph.sessionStore.list().size }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZcodeSpace.lg),
    ) {
        Row(Modifier.padding(bottom = ZcodeSpace.lg)) {
            Image(
                painter = painterResource(R.drawable.zcodea_mascot),
                contentDescription = "吉祥物",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
            Column(Modifier.padding(start = ZcodeSpace.md)) {
                Text("ZcodeAndriod", style = MaterialTheme.typography.titleLarge)
                Text(
                    "点开即用的手机 AI 编程助手",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StatusCard(
            title = "模型 API",
            detail = if (apiReady) "已配置，可以直接对话" else "尚未配置供应商",
            ok = apiReady,
            onOpen = { onNavigate(Page.SETTINGS) },
        )
        StatusCard(
            title = "AI 操作手机（无障碍）",
            detail = if (accessibilityOn) "已开启" else "未开启，去设备页授权",
            ok = accessibilityOn,
            onOpen = { onNavigate(Page.DEVICE) },
        )
        StatusCard(
            title = "屏幕截取",
            detail = if (captureOn) "授权中，AI 可按需看屏" else "未授权（可选）",
            ok = captureOn,
            onOpen = { onNavigate(Page.DEVICE) },
        )
        StatusCard(
            title = "工作区与会话",
            detail = "$sessionCount 个会话，沙箱在应用私有目录",
            ok = true,
            onOpen = { onNavigate(Page.CHAT) },
        )

        Spacer(Modifier.height(ZcodeSpace.xl))
        Text(
            "与智谱（Z.ai）/ ZCode 官方无隶属关系；吉祥物授权见仓库 branding/mascot/SOURCES.md。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(title: String, detail: String, ok: Boolean, onOpen: () -> Unit) {
    SectionPanel(title = title, modifier = Modifier.padding(bottom = ZcodeSpace.md)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text("前往") }
        }
    }
}

