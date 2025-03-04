package com.marketap.sdk.client.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.marketap.sdk.model.internal.push.AndroidPushButton
import com.marketap.sdk.model.internal.push.PushData
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MarketapPushNotificationBuilder(
    private val context: Context,
    private val data: PushData
) {
    private fun Intent.applyData(): Intent {
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

    private fun getUrlIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }.applyData()
    }

    private fun getDeepLinkIntent(deepLink: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }.applyData()
    }

    private fun getLaunchIntent(): Intent {
        return context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }?.applyData() ?: throw IllegalStateException("Launch intent not found")
    }

    private fun getPendingIntent(intent: Intent, flags: Int): PendingIntent {
        return PendingIntent.getActivity(context, 0, intent, flags)
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

    private fun getButtonIntent(button: AndroidPushButton): PendingIntent {
        val intent = when {
            button.url != null -> {
                getUrlIntent(button.url)
            }

            button.deepLink != null -> {
                getDeepLinkIntent(button.deepLink)
            }

            else -> {
                getLaunchIntent()
            }
        }
        return getPendingIntent(
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getContentIntent(deppLink: String?): PendingIntent {
        return getPendingIntent(
            deppLink?.let {
                getDeepLinkIntent(it)
            } ?: getLaunchIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun build(): Notification {
        val notificationBuilder =
            NotificationCompat.Builder(context, MarketapNotificationOpenHandler.CHANNEL_ID)
                .setSmallIcon(getDefaultAppIcon())
                .setContentTitle(data.title)
                .setContentText(data.body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        data.imageUrl?.let {
            notificationBuilder.setStyle(getPictureStyle(it))
        }

        data.buttons?.let {
            for (button in it) {
                notificationBuilder.addAction(0, button.name, getButtonIntent(button))
            }
        }


        notificationBuilder.setContentIntent(getContentIntent(data.deepLink))
        return notificationBuilder.build()
    }
}