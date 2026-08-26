package com.marketap.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marketap.sdk.client.push.MarketapPushNotificationBuilder
import com.marketap.sdk.client.push.PushImageDiagnostics
import com.marketap.sdk.model.internal.push.PushData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 이미지 로딩 결과가 실기기에서 실제로 올바르게 분류되는지 확인한다.
 * 이 분류값이 mkt_push_impression 의 mkt_image_result 로 나가므로,
 * 여기서 틀리면 원격 진단 전체가 무의미해진다.
 */
@RunWith(AndroidJUnit4::class)
class PushImageResultClassificationTest {

    private val tag = "PushImageDiag"

    private fun diagnose(imageUrl: String?): PushImageDiagnostics {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val builder = MarketapPushNotificationBuilder(
            context,
            PushData(
                title = "이미지 TEST",
                body = "이미지 TEST",
                imageUrl = imageUrl,
                notificationId = 1
            )
        )
        builder.build()
        Log.i(tag, "url=${imageUrl ?: "null"} -> ${builder.imageDiagnostics.describe()}")
        return builder.imageDiagnostics
    }

    @Test
    fun 정상_이미지는_ok로_분류된다() {
        val d = diagnose("https://static.marketap.io/images/dv0ok8v/57d0zik5.jpg")
        assertEquals(PushImageDiagnostics.RESULT_OK, d.result)
        assertEquals(200, d.httpCode)
        assertEquals("720x350", d.dimensions)
    }

    @Test
    fun 이미지가_없으면_none으로_분류된다() {
        assertEquals(PushImageDiagnostics.RESULT_NONE, diagnose(null).result)
        assertEquals(PushImageDiagnostics.RESULT_NONE, diagnose("").result)
        assertEquals(PushImageDiagnostics.RESULT_NONE, diagnose("   ").result)
    }

    /**
     * S3 는 ListBucket 권한이 없으면 없는 키에 404 가 아니라 403 을 준다.
     * 운영 데이터에서 http_error 403 은 "차단"뿐 아니라 "이미지가 삭제됨/경로 오류"일 수도 있다.
     */
    @Test
    fun 존재하지_않는_경로는_http_error로_분류된다() {
        val d = diagnose("https://static.marketap.io/images/dv0ok8v/does-not-exist.jpg")
        assertEquals(PushImageDiagnostics.RESULT_HTTP_ERROR, d.result)
        Log.i(tag, "http_error code=${d.httpCode}")
    }

    @Test
    fun 해석_불가_호스트는_exception으로_분류된다() {
        val d = diagnose("https://this-host-does-not-exist.marketap.invalid/a.jpg")
        assertEquals(PushImageDiagnostics.RESULT_EXCEPTION, d.result)
        Log.i(tag, "exception class=${d.errorClass}")
    }
}
