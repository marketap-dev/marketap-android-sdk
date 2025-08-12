package com.marketap.sdk.time

import com.marketap.sdk.utils.getNowByMillis
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateUtilTest {

    @Test
    fun `UTC 타임존에서 포맷이 올바르게 변환되는지`() {
        val currentMillis = 0L // 1970-01-01T00:00:00.000Z
        val expected = "1970-01-01T00:00:00.000Z"

        val result = getNowByMillis(currentMillis, TimeZone.getTimeZone("UTC"))

        assertEquals(expected, result)
    }

    @Test
    fun `KST 타임존에서 시간 차이가 반영되는지`() {
        val currentMillis = 0L
        val result = getNowByMillis(currentMillis, TimeZone.getTimeZone("Asia/Seoul"))

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        val expected = sdf.format(Date(currentMillis))

        assertEquals(expected, result)
    }

    @Test
    fun `현재 시간으로 호출 시 포맷이 올바른지`() {
        val now = System.currentTimeMillis()
        val result = getNowByMillis(now)

        // yyyy-MM-ddTHH:mm:ss.SSSZ 형태인지 검증
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")))
    }
}
