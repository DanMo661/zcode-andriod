package com.zcodeandriod.app.ui.device

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zcodeandriod.app.agent.ZcodeAAccessibilityService
import com.zcodeandriod.app.ui.components.SectionPanel
import com.zcodeandriod.app.ui.theme.ZcodeSpace

/**
 * 设备能力页：无障碍开关状态（以系统设置为唯一事实源，ContentObserver 实时刷新）
 * + 跳系统设置引导 + 能力说明。N3b 会加截屏授权卡。
 */
@Composable
fun DeviceScreen() {
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(false) }

    fun refresh() {
        accessibilityOn = ZcodeAAccessibilityService.isEnabled(context)
    }

    DisposableEffect(Unit) {
        refresh()
        val resolver = context.contentResolver
        val observer = object : android.database.ContentObserver(android.os.Handler(context.mainLooper)) {
            override fun deliverSelfNotifications(): Boolean = false
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZcodeSpace.lg),
    ) {
        SectionPanel(title = "AI 操作手机（无障碍）") {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (accessibilityOn) "已开启，AI 可以观察和操作屏幕" else "未开启",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (accessibilityOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "开启后，对话中的 AI 可以读取屏幕元素、替你点击和输入。" +
                            "支付、银行、密码界面会被强制拦截。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = ZcodeSpace.sm),
                    )
                }
            }
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.padding(top = ZcodeSpace.sm),
            ) {
                Text(if (accessibilityOn) "管理无障碍服务" else "去系统设置开启")
            }
        }

        SectionPanel(title = "屏幕截取", modifier = Modifier.padding(top = ZcodeSpace.lg)) {
            Text(
                "授权后，AI 在无障碍树信息不足时会截屏看图（截图只驻内存、30 秒内不取即弃）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = ZcodeSpace.sm),
            )
            var captureOn by remember { mutableStateOf(com.zcodeandriod.app.agent.ScreenCaptureService.ready()) }
            var pendingProjection by remember { mutableStateOf(false) }

            val projectionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val data = result.data
                if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
                    com.zcodeandriod.app.agent.ScreenCaptureService.start(context, result.resultCode, data)
                    captureOn = true
                }
            }
            val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { _ ->
                if (pendingProjection) {
                    pendingProjection = false
                    context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        ?.createScreenCaptureIntent()
                        ?.let { projectionLauncher.launch(it) }
                }
            }

            Row {
                Button(
                    enabled = !captureOn,
                    onClick = {
                        val needNotif = android.os.Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (needNotif) {
                            pendingProjection = true
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                                ?.createScreenCaptureIntent()
                                ?.let { projectionLauncher.launch(it) }
                        }
                    },
                ) { Text(if (captureOn) "截屏授权已开启" else "开启屏幕截取") }
                if (captureOn) {
                    TextButton(onClick = {
                        com.zcodeandriod.app.agent.ScreenCaptureService.stop(context)
                        captureOn = false
                    }) { Text("停止") }
                }
            }
        }

        SectionPanel(title = "隐私边界", modifier = Modifier.padding(top = ZcodeSpace.lg)) {
            Column {
                Bullet("系统受保护界面（状态栏、安装器、权限管理）拒绝读取与操作")
                Bullet("疑似密码/验证码的文本会先脱敏成 [redacted] 再交给模型")
                Bullet("支付与银行上下文的点击/长按一律拒绝")
                Bullet("对话记录只存在本机；工具结果落盘前做了脱敏投影（N4）")
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("· ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
