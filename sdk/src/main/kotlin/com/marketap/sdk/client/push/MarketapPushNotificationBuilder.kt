package com.marketap.sdk.client.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.marketap.sdk.model.internal.push.AndroidPushButton
import com.marketap.sdk.model.internal.push.PushData
import com.marketap.sdk.utils.ManifestUtils
import com.marketap.sdk.utils.logger
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class MarketapPushNotificationBuilder(
    private val context: Context,
    private val data: PushData
) {
    private fun Intent.applyData(
        deepLink: String? = null,
        url: String? = null
    ): Intent {
        putExtra(MarketapNotificationOpenHandler.NOTIFICATION_DEEP_LINK_KEY, deepLink)
        putExtra(MarketapNotificationOpenHandler.NOTIFICATION_URL_KEY, url)
        putExtra(MarketapNotificationOpenHandler.IS_NOTIFICATION_FROM_MARKETAP, true)
        putExtra(
            MarketapNotificationOpenHandler.NOTIFICATION_ID_KEY,
            this@MarketapPushNotificationBuilder.data.notificationId
        )
        putExtra(
            MarketapNotificationOpenHandler.CAMPAIGN_KEY,
            this@MarketapPushNotificationBuilder.data.deliveryData
        )
        return this
    }

    private fun getNotificationIcon(): Int {
        val customIcon = ManifestUtils.getSystemResource(
            context, ManifestUtils.SystemResourceConstant.NOTIFICATION_ICON
        )
        if (customIcon != 0) {
            return customIcon
        }
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            appInfo.icon
        } catch (e: PackageManager.NameNotFoundException) {
            android.R.drawable.stat_notify_chat
        }
    }

    private fun createIntent(
        deepLink: String? = null,
        url: String? = null
    ): Intent {
        val intent = Intent(context, MarketapTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }.applyData(
            deepLink = deepLink,
            url = url
        )
        return intent
    }

    private fun getPendingIntent(intent: Intent, requestCode: Int, flags: Int): PendingIntent {
        return PendingIntent.getActivity(context, data.notificationId + requestCode, intent, flags)
    }

    private fun loadBitmapFromUrl(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                doInput = true
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                logger.w { "Failed to load push image: HTTP $responseCode for $url" }
                return null
            }

            // raw 네트워크 스트림을 BufferedInputStream으로 감싼다.
            // BitmapFactory.decodeStream이 버퍼링 안 된 스트림에서 부분 read(skip) 시
            // null을 반환하는 고전 버그를 회피 (일부 OS/기기 네트워크 스택에서만 재현).
            BufferedInputStream(connection.inputStream).use { input ->
                BitmapFactory.decodeStream(input).also {
                    if (it == null) {
                        logger.w { "Push image decode returned null for $url" }
                    }
                }
            }
        } catch (t: Throwable) {
            // IOException뿐 아니라 OutOfMemoryError 등 모든 실패를 로그로 남겨 진단 가능하게 함
            logger.e(t) { "Failed to load push image for $url" }
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getButtonIntent(index: Int, button: AndroidPushButton): PendingIntent {
        val intent = when {
            button.url != null -> {
                createIntent(url = button.url)
            }

            button.deepLink != null -> {
                createIntent(deepLink = button.deepLink)
            }

            else -> {
                createIntent()
            }
        }
        return getPendingIntent(
            intent,
            index + 1,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getContentIntent(deepLink: String?): PendingIntent {
        return getPendingIntent(
            createIntent(deepLink = deepLink),
            0,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun build(): Notification {
        val notificationBuilder =
            NotificationCompat.Builder(
                context, ManifestUtils.getSystemString(
                    context, ManifestUtils.SystemStringConstant.CHANNEL_ID
                )
            )
                .setSmallIcon(getNotificationIcon())
                .setContentTitle(data.title)
                .setContentText(data.body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        val image = data.imageUrl?.let { loadBitmapFromUrl(it) }
        val style = if (image != null) {
            // 접힌 알림에서 썸네일로 이미지 노출 (iOS 첨부 동작과 동일하게 맞춤)
            notificationBuilder.setLargeIcon(image)
            NotificationCompat.BigPictureStyle()
                .bigPicture(image)
                // 펼쳤을 때는 largeIcon을 숨겨 큰 이미지만 보이게 함
                .bigLargeIcon(null as Bitmap?)
                .setSummaryText(data.body)
        } else {
            // 이미지가 없거나 로딩 실패 시 본문 전체를 보여주는 BigTextStyle로 폴백
            NotificationCompat.BigTextStyle().bigText(data.body)
        }
        notificationBuilder.setStyle(style)

        data.buttons?.let {
            it.mapIndexed { idx, button ->
                notificationBuilder.addAction(0, button.name, getButtonIntent(idx, button))
            }
        }


        notificationBuilder.setContentIntent(getContentIntent(data.deepLink))
        return notificationBuilder.build()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}