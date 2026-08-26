package com.marketap.sdk.client.push

import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marketap.sdk.model.internal.push.PushData
import com.marketap.sdk.utils.logger

class MarketapFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            logger.d { "MarketapFirebaseMessagingService: onMessageReceived called" }
            handleMarketapRemoteMessage(applicationContext, remoteMessage)
        } catch (t: Throwable) {
            logger.e(t) { "MarketapFirebaseMessagingService: failed to handle message" }
        }
    }

    override fun onNewToken(token: String) {
        /* Do Nothing */
    }

    companion object {
        @JvmStatic
        fun handleMarketapRemoteMessage(context: Context, remoteMessage: RemoteMessage): Boolean {
            logger.d {
                "MarketapFirebaseMessagingCompanion: Received remote message, " +
                        "data: ${remoteMessage.data}"
            }
            if (!isMarketapPushNotification(remoteMessage)) {
                logger.d { "Not a Marketap push notification, ignoring" }
                return false
            }

            logger.d { "Marketap push notification detected, processing" }
            return try {
                handleMarketapPush(context, remoteMessage.data)
                true
            } catch (t: Throwable) {
                logger.e(t) { "Failed to handle Marketap push notification" }
                false
            }
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

            // 이미지 로딩 결과를 임프레션에 실어야 하므로 build() 를 먼저 돌린다.
            // 임프레션 전송 자체는 IO 코루틴이라 순서를 바꿔도 알림 표시가 늦어지지 않는다.
            val builder = MarketapPushNotificationBuilder(context, pushData)
            val marketapPushNotification = try {
                builder.build()
            } catch (t: Throwable) {
                logger.e(t) { "Failed to build Marketap push notification for ID ${pushData.notificationId}" }
                null
            }

            // build() 가 실패해도 임프레션은 남긴다. 예전처럼 build 실패가 곧 이벤트 유실이 되면
            // 실패율을 관측할 수 없다.
            PushTracker.trackImpression(context, pushData, builder.imageDiagnostics)

            if (marketapPushNotification == null) return

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(pushData.notificationId, marketapPushNotification)
        }
    }
}
