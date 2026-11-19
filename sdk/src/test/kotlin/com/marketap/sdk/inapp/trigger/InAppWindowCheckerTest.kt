package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.condition.InAppWindowChecker
import com.marketap.sdk.model.internal.inapp.SendableTimeWindow
import com.marketap.sdk.model.internal.inapp.TimeRange
import com.marketap.sdk.model.internal.inapp.WeeklyTimeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class InAppWindowCheckerTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(seoul).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun checker(millis: Long) = InAppWindowChecker({ millis }, { seoul })

    private val mondayNineToSix = SendableTimeWindow(
        listOf(WeeklyTimeRule("MONDAY", listOf(TimeRange(9 * 60, 18 * 60))))
    )

    @Test
    fun `시간대가 없으면 항상 허용`() {
        assertTrue(checker(at(2026, 11, 9, 3, 0)).check(null))
    }

    @Test
    fun `해당 요일의 시간대 안이면 허용, 양 끝 포함`() {
        assertTrue(checker(at(2026, 11, 9, 9, 0)).check(mondayNineToSix))
        assertTrue(checker(at(2026, 11, 9, 18, 0)).check(mondayNineToSix))
    }

    @Test
    fun `해당 요일이어도 시간대 밖이면 거부`() {
        assertFalse(checker(at(2026, 11, 9, 8, 59)).check(mondayNineToSix))
        assertFalse(checker(at(2026, 11, 9, 18, 1)).check(mondayNineToSix))
    }

    @Test
    fun `규칙이 없는 요일은 거부`() {
        assertFalse(checker(at(2026, 11, 10, 12, 0)).check(mondayNineToSix))
    }
}
