package com.zcodeandriod.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.zcodeandriod.app.R

/**
 * 通知渠道唯一注册处：全应用只在这里建渠道，
 * 不学 dsh-go 四处 object 各自建渠道的做法。
 */
object AppNotifications {
    const val CHANNEL_CHAT = "chat"
    const val CHANNEL_CAPTURE = "capture"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHAT,
                context.getString(R.string.notif_channel_chat),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CAPTURE,
                context.getString(R.string.notif_channel_capture),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun captureNotification(context: Context): Notification =
        android.app.Notification.Builder(context, CHANNEL_CAPTURE)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("屏幕截取进行中")
            .setContentText("AI 需要查看屏幕时才会取帧；可在应用内停止")
            .setOngoing(true)
            .build()
}
