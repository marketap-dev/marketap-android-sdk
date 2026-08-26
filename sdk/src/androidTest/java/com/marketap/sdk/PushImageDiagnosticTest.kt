package com.marketap.sdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marketap.sdk.client.push.MarketapPushNotificationBuilder
import com.marketap.sdk.model.internal.push.PushData
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 푸시 이미지 미표시 진단용. 실제 장애 이미지 URL을 대상으로
 * (1) 현재 배포된 loadBitmapFromUrl, (2) 전량 버퍼링 후 decodeByteArray 를 비교한다.
 *
 * NT-789 후속. 재현되면 여기서 바로 고칠 수 있고, 재현이 안 되면
 * OS 레벨 문제가 아니라는 것이 확정된다.
 */
@RunWith(AndroidJUnit4::class)
class PushImageDiagnosticTest {

    private val tag = "PushImageDiag"

    // 2026-08-26 15:00 KST 테스트 발송에 실제로 쓰인 이미지 (progressive JPEG, 720x350, 72911B)
    private val failingUrl = "https://static.marketap.io/images/dv0ok8v/57d0zik5.jpg"

    // 대조군: 같은 CDN의 baseline JPEG
    private val baselineUrl = "https://static.marketap.io/images/hbsi6h2/htcd3vuf.jpg"

    // 대조군: PNG
    private val pngUrl = "https://static.marketap.io/images/cavwwe4/yefacauu.png"

    @Test
    fun diagnosePushImageDecoding() {
        Log.i(tag, "=== device: ${android.os.Build.MODEL} / SDK ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE}) ===")

        listOf(
            "progressive-jpeg" to failingUrl,
            "baseline-jpeg" to baselineUrl,
            "png" to pngUrl
        ).forEach { (label, url) ->
            Log.i(tag, "---------- $label : $url ----------")
            repeat(3) { attempt ->
                Log.i(tag, "[$label] attempt ${attempt + 1}")
                probeHttp(label, url)
                viaShippedImpl(label, url)
                viaByteArray(label, url)
            }
        }
    }

    /** 네트워크 계층만 따로 확인: 응답 코드, 헤더, 실제 수신 바이트 수 */
    private fun probeHttp(label: String, url: String) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                doInput = true
            }
            conn.connect()
            val code = conn.responseCode
            val type = conn.contentType
            val declaredLen = conn.contentLength
            val body = conn.inputStream.use { it.readBytes() }
            Log.i(
                tag,
                "[$label] HTTP $code type=$type contentLength=$declaredLen actualRead=${body.size} " +
                        "firstBytes=${body.take(4).joinToString(" ") { b -> "%02x".format(b) }} " +
                        "lastBytes=${body.takeLast(2).joinToString(" ") { b -> "%02x".format(b) }}"
            )
        } catch (t: Throwable) {
            Log.e(tag, "[$label] HTTP probe failed: ${t.javaClass.name}: ${t.message}", t)
        } finally {
            conn?.disconnect()
        }
    }

    /** 현재 배포된 구현(1.4.2)을 리플렉션으로 그대로 호출 */
    private fun viaShippedImpl(label: String, url: String) {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val pushData = PushData(
                title = "이미지 TEST",
                body = "이미지 TEST",
                imageUrl = url,
                notificationId = 1
            )
            val builder = MarketapPushNotificationBuilder(context, pushData)
            val method = MarketapPushNotificationBuilder::class.java
                .getDeclaredMethod("loadBitmapFromUrl", String::class.java)
            method.isAccessible = true
            val started = System.nanoTime()
            val bitmap = method.invoke(builder, url) as Bitmap?
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            Log.i(
                tag,
                "[$label] SHIPPED loadBitmapFromUrl -> " +
                        (bitmap?.let { "${it.width}x${it.height} config=${it.config} bytes=${it.byteCount}" } ?: "NULL") +
                        " (${elapsedMs}ms)"
            )
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            Log.e(tag, "[$label] SHIPPED impl threw: ${cause.javaClass.name}: ${cause.message}", cause)
        }
    }

    /** 후보 수정안: 전량을 메모리로 읽은 뒤 decodeByteArray */
    private fun viaByteArray(label: String, url: String) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                doInput = true
            }
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(tag, "[$label] BYTEARRAY skipped, HTTP $code")
                return
            }
            val started = System.nanoTime()
            val buffer = ByteArrayOutputStream()
            BufferedInputStream(conn.inputStream).use { input -> input.copyTo(buffer) }
            val bytes = buffer.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            Log.i(
                tag,
                "[$label] BYTEARRAY decodeByteArray(${bytes.size}B) -> " +
                        (bitmap?.let { "${it.width}x${it.height} config=${it.config} bytes=${it.byteCount}" } ?: "NULL") +
                        " (${elapsedMs}ms)"
            )
        } catch (t: Throwable) {
            Log.e(tag, "[$label] BYTEARRAY failed: ${t.javaClass.name}: ${t.message}", t)
        } finally {
            conn?.disconnect()
        }
    }
}
