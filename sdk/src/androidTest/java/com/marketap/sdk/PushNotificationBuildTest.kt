package com.marketap.sdk

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marketap.sdk.client.push.MarketapPushNotificationBuilder
import com.marketap.sdk.model.internal.push.PushData
import org.junit.Test
import org.junit.runner.RunWith

/**
 * build() 가 실제로 만들어내는 Notification 에 이미지가 실려 있는지 확인한다.
 * 5c40829 에서 추가한 setLargeIcon + bigLargeIcon(null) 조합이
 * 큰 이미지를 지워버리지 않는지 검증하는 것이 목적.
 */
@RunWith(AndroidJUnit4::class)
class PushNotificationBuildTest {

    private val tag = "PushImageDiag"
    private val imageUrl = "https://static.marketap.io/images/dv0ok8v/57d0zik5.jpg"

    @Test
    fun notificationCarriesImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pushData = PushData(
            title = "이미지 TEST",
            body = "이미지 TEST",
            imageUrl = imageUrl,
            notificationId = 424242
        )

        val notification = MarketapPushNotificationBuilder(context, pushData).build()
        val extras = notification.extras

        Log.i(tag, "=== notification extras (SDK ${Build.VERSION.SDK_INT}) ===")
        Log.i(tag, "template      = ${extras.getString(Notification.EXTRA_TEMPLATE)}")
        Log.i(tag, "title         = ${extras.getCharSequence(Notification.EXTRA_TITLE)}")
        Log.i(tag, "text          = ${extras.getCharSequence(Notification.EXTRA_TEXT)}")

        val largeIcon = notification.getLargeIcon()
        Log.i(tag, "EXTRA_LARGE_ICON(getLargeIcon) = ${largeIcon?.type?.let { "Icon(type=$it)" } ?: "NULL"}")

        @Suppress("DEPRECATION")
        val bigPictureBmp = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        Log.i(
            tag,
            "EXTRA_PICTURE = " + (bigPictureBmp?.let { "${it.width}x${it.height} bytes=${it.byteCount}" } ?: "NULL")
        )

        if (Build.VERSION.SDK_INT >= 31) {
            val pictureIcon = extras.getParcelable<android.graphics.drawable.Icon>("android.pictureIcon")
            Log.i(tag, "EXTRA_PICTURE_ICON = ${pictureIcon?.let { "Icon(type=${it.type})" } ?: "NULL"}")
        }

        @Suppress("DEPRECATION")
        val bigLargeIcon = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON_BIG)
        Log.i(
            tag,
            "EXTRA_LARGE_ICON_BIG = " + (bigLargeIcon?.let { "${it.width}x${it.height}" } ?: "NULL (의도된 값)")
        )

        Log.i(tag, "all extra keys = ${extras.keySet().sorted()}")

        // 실제로 시스템에 올려서 거부되지 않는지 확인
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        try {
            nm.notify(pushData.notificationId, notification)
            Log.i(tag, "notify() 성공")
            val active = nm.activeNotifications.firstOrNull { it.id == pushData.notificationId }
            Log.i(tag, "activeNotifications 에 존재? ${active != null}")
            active?.notification?.extras?.let { posted ->
                @Suppress("DEPRECATION")
                val postedPic = posted.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
                Log.i(
                    tag,
                    "게시 후 EXTRA_PICTURE = " +
                            (postedPic?.let { "${it.width}x${it.height}" } ?: "NULL ← 시스템이 제거함")
                )
            }
        } catch (t: Throwable) {
            Log.e(tag, "notify() 실패: ${t.javaClass.name}: ${t.message}", t)
        }
    }
}
