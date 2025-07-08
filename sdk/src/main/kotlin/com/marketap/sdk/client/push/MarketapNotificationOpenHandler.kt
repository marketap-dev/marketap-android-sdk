package com.marketap.sdk.client.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import com.marketap.sdk.utils.ManifestUtils
import com.marketap.sdk.utils.logger

internal class MarketapNotificationOpenHandler(
    context: Context
) {
    init {
        createNotificationChannel(context)
    }

    companion object {
        const val NOTIFICATION_ID_KEY = "_marketap_notification_id"
        const val IS_NOTIFICATION_FROM_MARKETAP = "_is_notification_from_marketap"
        const val CAMPAIGN_KEY = "_marketap_campaign_id"
        const val NOTIFICATION_DEEP_LINK_KEY = "_marketap_notification_deep_link"
        const val NOTIFICATION_URL_KEY = "_marketap_notification_url"
    }

    private fun createNotificationChannel(context: Context) {
        val channelId = ManifestUtils.getSystemString(
            context, ManifestUtils.SystemStringConstant.CHANNEL_ID
        )
        val channelName = ManifestUtils.getSystemString(
            context, ManifestUtils.SystemStringConstant.CHANNEL_NAME
        )
        val channelDescription = ManifestUtils.getSystemString(
            context, ManifestUtils.SystemStringConstant.CHANNEL_DESC
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            // 채널이 이미 존재하는지 확인
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = channelDescription
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                }

                notificationManager.createNotificationChannel(channel)
                logger.i {
                    "Notification channel created, " +
                            "ID: $channelId, Name: $channelName, Description: $channelDescription"
                }
            } else {
                logger.d {
                    "Notification channel already exists, " +
                            "ID: $channelId, Name: $channelName, Description: $channelDescription"
                }
            }
        }
    }
}