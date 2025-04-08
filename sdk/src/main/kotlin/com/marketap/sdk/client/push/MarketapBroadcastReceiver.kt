package com.marketap.sdk.client.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.RemoteMessage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.push.PushData

import com.marketap.sdk.utils.getNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarketapBroadcastReceiver : BroadcastReceiver() {
    private val marketapApi = MarketapApiImpl()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val remoteMessage = RemoteMessage(intent.extras)
        if (createNotification(remoteMessage, context)) {
            abortBroadcast()
        }
    }

    private fun createNotification(remoteMessage: RemoteMessage, context: Context): Boolean {
        remoteMessage.data.let { data ->
            if (data["source"] == "marketap") {  // Marketap 플랫폼에서 보낸 메시지만 처리
                CoroutineScope(Dispatchers.IO).launch {
                    handleCustomPush(context, data)
                }
                return true
            }
        }
        return false
    }

    private suspend fun handleCustomPush(context: Context, data: Map<String, String>) {
        val pushData = PushData.fromMap(data) ?: return
        track(pushData)
        val marketapPushNotification = MarketapPushNotificationBuilder(context, pushData).build()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(pushData.notificationId, marketapPushNotification)
    }

    private suspend fun track(data: PushData) {
        data.deliveryData?.let {
            marketapApi.track(
                it.projectId,
                IngestEventRequest.impression(
                    it.userId,
                    DeviceReq(it.deviceId),
                    AppEventProperty.offSite(it),
                    getNow()
                )
            )
        }
    }
}
