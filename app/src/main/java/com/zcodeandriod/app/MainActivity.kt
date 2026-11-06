package com.zcodeandriod.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zcodeandriod.app.notifications.AppNotifications
import com.zcodeandriod.app.ui.App
import com.zcodeandriod.app.ui.theme.ZcodeATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppNotifications.ensureChannels(this)
        setContent {
            ZcodeATheme {
                App()
            }
        }
    }
}
