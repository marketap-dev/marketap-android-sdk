package com.marketap.sdk.client.push

import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marketap.sdk.model.internal.push.PushData
import com.marketap.sdk.utils.logger

class MarketapFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        handleMarketapRemoteMessage(applicationContext, remoteMessage)
    }

    override fun onNewToken(token: String) {
        /* Do Nothing */
    }

    companion object {
        @JvmStatic
        fun handleMarketapRemoteMessage(context: Context, remoteMessage: RemoteMessage): Boolean {
            logger.d {
                "MarketapFirebaseMessagingService: Received remote message, " +
                        "data: ${remoteMessage.data}"
            }
            if (!isMarketapPushNotification(remoteMessage)) {
                logger.d { "Not a Marketap push notification, ignoring" }
                return false
            }

            logger.d { "Marketap push notification detected, processing" }
            handleMarketapPush(context, remoteMessage.data)
            return true
        }

        @JvmStatic
        fun isMarketapPushNotification(remoteMessage: RemoteMessage): Boolean {
            return remoteMessage.data["source"] == "marketap"
        }

        private fun handleMarketapPush(context: Context, data: Map<String, String>) {
            val pushData = PushData.fromMap(data)
            if (pushData == null) {
                logger.w { "Received invalid Marketap push notification data, ignoring" }
                return
            }

            if (PushDedupStore.isDuplicate(context, pushData.notificationId.toString())) {
                logger.w {
                    "Marketap push notification with ID ${pushData.notificationId} is a duplicate, ignoring"
                }
                return
            }

            PushTracker.trackImpression(context, pushData)
            val marketapPushNotification = try {
                MarketapPushNotificationBuilder(context, pushData).build()
            } catch (e: Exception) {
                logger.e(e) { "Failed to build Marketap push notification for ID ${pushData.notificationId}" }
                return
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(pushData.notificationId, marketapPushNotification)
        }
    }
}