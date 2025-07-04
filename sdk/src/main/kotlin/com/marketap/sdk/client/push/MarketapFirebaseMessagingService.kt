package com.marketap.sdk.client.push

import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.push.PushData
import com.marketap.sdk.presentation.MarketapRegistry.config
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.getNow
import com.marketap.sdk.utils.pairAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarketapFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        handleMarketapRemoteMessage(applicationContext, remoteMessage)
    }

    override fun onNewToken(token: String) {
        /* Do Nothing */
    }

    companion object {
        private val marketapApi = MarketapApiImpl(debug = config?.debug == true)

        @JvmStatic
        fun handleMarketapRemoteMessage(context: Context, remoteMessage: RemoteMessage): Boolean {
            if (!isMarketapPushNotification(remoteMessage)) {
                return false
            }

            handleMarketapPush(context, remoteMessage.data)
            return true
        }

        @JvmStatic
        fun isMarketapPushNotification(remoteMessage: RemoteMessage): Boolean {
            return remoteMessage.data["source"] == "marketap"
        }

        private fun handleMarketapPush(context: Context, data: Map<String, String>) {
            val pushData = PushData.fromMap(data) ?: return
            if (PushDedupStore.isDuplicate(context, pushData.notificationId.toString())) {
                return
            }

            track(context, pushData)
            val marketapPushNotification =
                MarketapPushNotificationBuilder(context, pushData).build()
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(pushData.notificationId, marketapPushNotification)
        }

        private fun track(context: Context, data: PushData) {

            data.deliveryData?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        marketapApi.track(
                            it.projectId,
                            IngestEventRequest.impression(
                                it.userId,
                                DeviceReq(it.deviceId),
                                AppEventProperty.offSite(it),
                                getNow()
                            )
                        )
                    } catch (e: Exception) {
                        val storage = SharedPreferenceInternalStorage(context)
                        storage.queueItem(
                            "events", PairEntry(
                                it.projectId, IngestEventRequest.impression(
                                    it.userId,
                                    DeviceReq(it.deviceId),
                                    AppEventProperty.offSite(it),
                                    getNow()
                                )
                            ), pairAdapter()
                        )
                    }
                }
            }
        }
    }
}