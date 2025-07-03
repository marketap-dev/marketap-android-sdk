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
import java.io.IOException
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

    private fun getDefaultAppIcon(): Int {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            appInfo.icon // 🔥 호스트 앱의 기본 아이콘 가져오기
        } catch (e: PackageManager.NameNotFoundException) {
            android.R.drawable.stat_notify_chat // 기본 아이콘 (예비용)
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
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            null
        }
    }

    private fun getPictureStyle(imageUrl: String): NotificationCompat.BigPictureStyle {
        return NotificationCompat.BigPictureStyle()
            .bigPicture(loadBitmapFromUrl(imageUrl)) // 비동기 로딩
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
                .setSmallIcon(getDefaultAppIcon())
                .setContentTitle(data.title)
                .setContentText(data.body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        data.imageUrl?.let {
            notificationBuilder.setStyle(getPictureStyle(it))
        }

        data.buttons?.let {
            it.mapIndexed { idx, button ->
                notificationBuilder.addAction(0, button.name, getButtonIntent(idx, button))
            }
        }


        notificationBuilder.setContentIntent(getContentIntent(data.deepLink))
        return notificationBuilder.build()
    }
}