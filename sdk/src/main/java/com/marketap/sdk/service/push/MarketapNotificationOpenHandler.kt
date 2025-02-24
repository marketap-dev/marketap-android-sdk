package com.marketap.sdk.service.push

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import com.marketap.sdk.api.MarketapBackend
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.service.inapp.comparison.types.util.getNow

internal class MarketapNotificationOpenHandler(
    private val marketapBackend: MarketapBackend,
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

    fun maybeClickPush(activity: Activity) {
        val intent = activity.intent
        if (intent.getBooleanExtra(IS_NOTIFICATION_FROM_MARKETAP, false)) {
            val notificationManager =
                activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val data: DeliveryData? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(CAMPAIGN_KEY, DeliveryData::class.java)
            } else {
                intent.getSerializableExtra(CAMPAIGN_KEY) as? DeliveryData?
            }

            if (data != null) {
                marketapBackend.track(
                    data.projectId,
                    IngestEventRequest.click(
                        data.userId,
                        DeviceReq(data.deviceId),
                        AppEventProperty.offSite(data.campaignId).addLocationId("push"),
                        getNow()
                    )
                )
            }
            notificationManager.cancel(intent.getIntExtra(NOTIFICATION_ID_KEY, 0))
        }
    }
}