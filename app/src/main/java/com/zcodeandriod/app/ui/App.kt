package com.zcodeandriod.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.zcodeandriod.app.R
import com.zcodeandriod.app.core.Graph
import com.zcodeandriod.app.ui.chat.ChatScreen
import com.zcodeandriod.app.ui.chat.ChatViewModel
import com.zcodeandriod.app.ui.device.DeviceScreen
import com.zcodeandriod.app.ui.launcher.LauncherScreen
import com.zcodeandriod.app.ui.settings.SettingsScreen

/** 类型安全路由：不学字符串魔法页面名 */
enum class Page(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    CHAT(R.string.nav_chat, R.drawable.ic_nav_chat),
    DEVICE(R.string.nav_device, R.drawable.ic_nav_device),
    LAUNCHER(R.string.nav_launcher, R.drawable.ic_nav_launcher),
    SETTINGS(R.string.nav_settings, R.drawable.ic_nav_settings),
}

@Composable
fun App() {
    var page by rememberSaveable { mutableStateOf(Page.CHAT) }
    // 进程级对话 VM：App() 不随 tab 切换销毁，remember 在此层存活
    val chatViewModel = remember { ChatViewModel(Graph.appScope) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = page == p,
                        onClick = { page = p },
                        icon = {
                            Icon(
                                painter = painterResource(p.iconRes),
                                contentDescription = stringResource(p.labelRes),
                            )
                        },
                        label = { Text(stringResource(p.labelRes)) },
                    )
                }
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when (page) {
                Page.CHAT -> ChatScreen(chatViewModel)
                Page.DEVICE -> DeviceScreen()
                Page.LAUNCHER -> LauncherScreen(onNavigate = { page = it })
                Page.SETTINGS -> SettingsScreen()
            }
        }
    }
}
