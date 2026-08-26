package com.marketap.sdk.client.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
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
    /**
     * 직전 build() 에서 이미지 로딩이 어떻게 끝났는지. 임프레션 이벤트에 실어 보내
     * 기기 접근 없이 원격에서 실패 원인을 판별하기 위한 계측값이다.
     * build() 호출 전에는 NOT_ATTEMPTED 상태로 남는다.
     */
    internal var imageDiagnostics: PushImageDiagnostics = PushImageDiagnostics.notAttempted()
        private set

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
        // 시스템 시각 변경에 영향받지 않도록 단조시계 사용
        val startedAt = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - startedAt

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
                imageDiagnostics = PushImageDiagnostics.httpError(responseCode, elapsed())
                logger.w { "Failed to load push image: HTTP $responseCode for $url" }
                return null
            }

            // raw 네트워크 스트림을 BufferedInputStream으로 감싼다.
            // BitmapFactory.decodeStream이 버퍼링 안 된 스트림에서 부분 read(skip) 시
            // null을 반환하는 고전 버그를 회피 (일부 OS/기기 네트워크 스택에서만 재현).
            BufferedInputStream(connection.inputStream).use { input ->
                BitmapFactory.decodeStream(input).also { bitmap ->
                    imageDiagnostics = if (bitmap == null) {
                        logger.w { "Push image decode returned null for $url" }
                        PushImageDiagnostics.decodeNull(responseCode, elapsed())
                    } else {
                        PushImageDiagnostics.ok(responseCode, elapsed(), bitmap)
                    }
                }
            }
        } catch (t: Throwable) {
            // IOException뿐 아니라 OutOfMemoryError 등 모든 실패를 로그로 남겨 진단 가능하게 함
            imageDiagnostics = PushImageDiagnostics.exception(t, elapsed())
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

        // 빈 문자열/공백 URL 이 URL(...) 에서 예외로 튀어 exception 으로 잘못 분류되는 것을 막는다.
        val imageUrl = data.imageUrl?.takeIf { it.isNotBlank() }
        if (imageUrl == null) {
            imageDiagnostics = PushImageDiagnostics.none()
        }
        val image = imageUrl?.let { loadBitmapFromUrl(it) }

        // 성공/실패 상관없이 항상 남긴다. 테스트 발송이든 실발송이든 logcat 한 줄로 판별 가능해야 한다.
        logger.i {
            "Push image load result for notificationId=${data.notificationId}: " +
                    "${imageDiagnostics.describe()} (url=${imageUrl ?: "none"})"
        }
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

/**
 * 푸시 이미지 로딩 결과. mkt_push_impression 이벤트 속성으로 전송되어
 * 실기기 접근 없이 실패 원인(네트워크/HTTP/디코드)을 원격에서 판별하는 데 쓰인다.
 */
internal data class PushImageDiagnostics(
    val result: String,
    val httpCode: Int? = null,
    val errorClass: String? = null,
    val elapsedMs: Long? = null,
    val dimensions: String? = null,
    val bytes: Int? = null,
) {
    fun describe(): String = buildString {
        append(result)
        httpCode?.let { append(" http=").append(it) }
        errorClass?.let { append(" error=").append(it) }
        dimensions?.let { append(" size=").append(it) }
        bytes?.let { append(" bytes=").append(it) }
        elapsedMs?.let { append(" ").append(it).append("ms") }
    }

    /**
     * 이벤트 속성으로 평탄화. null 값은 컬럼을 늘리지 않도록 제외한다.
     *
     * buildMap 을 쓰지 않는다. 수신 객체가 MutableMap 이 되면 프로퍼티 이름이
     * Map 멤버(size 등)에 가려져 엉뚱한 값이 실린다.
     */
    fun toEventProperties(): Map<String, Any> {
        val properties = mutableMapOf<String, Any>(KEY_RESULT to result)
        httpCode?.let { properties[KEY_HTTP_CODE] = it }
        errorClass?.let { properties[KEY_ERROR] = it }
        elapsedMs?.let { properties[KEY_ELAPSED_MS] = it }
        dimensions?.let { properties[KEY_SIZE] = it }
        bytes?.let { properties[KEY_BYTES] = it }
        return properties
    }

    companion object {
        const val KEY_RESULT = "mkt_image_result"
        const val KEY_HTTP_CODE = "mkt_image_http_code"
        const val KEY_ERROR = "mkt_image_error"
        const val KEY_ELAPSED_MS = "mkt_image_elapsed_ms"
        const val KEY_SIZE = "mkt_image_size"
        const val KEY_BYTES = "mkt_image_bytes"

        /** build() 가 아직 호출되지 않음 */
        const val RESULT_NOT_ATTEMPTED = "not_attempted"

        /** 캠페인에 이미지가 없음 (정상) */
        const val RESULT_NONE = "none"
        const val RESULT_OK = "ok"
        const val RESULT_HTTP_ERROR = "http_error"
        const val RESULT_DECODE_NULL = "decode_null"
        const val RESULT_EXCEPTION = "exception"

        fun notAttempted() = PushImageDiagnostics(RESULT_NOT_ATTEMPTED)
        fun none() = PushImageDiagnostics(RESULT_NONE)

        fun ok(httpCode: Int, elapsedMs: Long, bitmap: Bitmap) = PushImageDiagnostics(
            result = RESULT_OK,
            httpCode = httpCode,
            elapsedMs = elapsedMs,
            dimensions = "${bitmap.width}x${bitmap.height}",
            bytes = bitmap.byteCount,
        )

        fun httpError(httpCode: Int, elapsedMs: Long) =
            PushImageDiagnostics(RESULT_HTTP_ERROR, httpCode = httpCode, elapsedMs = elapsedMs)

        fun decodeNull(httpCode: Int, elapsedMs: Long) =
            PushImageDiagnostics(RESULT_DECODE_NULL, httpCode = httpCode, elapsedMs = elapsedMs)

        /**
         * 예외는 클래스명만 보낸다. 메시지에는 URL·호스트 등이 섞여 들어올 수 있어
         * 이벤트 속성으로 내보내기에 적절치 않다.
         */
        fun exception(t: Throwable, elapsedMs: Long) =
            PushImageDiagnostics(
                RESULT_EXCEPTION,
                errorClass = t.javaClass.simpleName,
                elapsedMs = elapsedMs,
            )
    }
}