package com.marketap.sdk.client.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build

internal class MarketapNotificationOpenHandler(
    context: Context
) {
    init {
        createNotificationChannel(context)
    }

    companion object {
        const val CHANNEL_ID = "marketap"
        const val CHANNEL_NAME = "Marketap Notifications"
        const val CHANNEL_DESC = "Push notifications from Marketap SDK"
        const val NOTIFICATION_ID_KEY = "_marketap_notification_id"
        const val IS_NOTIFICATION_FROM_MARKETAP = "_is_notification_from_marketap"
        const val CAMPAIGN_KEY = "_marketap_campaign_id"
        const val NOTIFICATION_DEEP_LINK_KEY = "_marketap_notification_deep_link"
        const val NOTIFICATION_URL_KEY = "_marketap_notification_url"
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            // 채널이 이미 존재하는지 확인
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                }

                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}