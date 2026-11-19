package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.SendableTimeWindow
import java.util.Calendar
import java.util.TimeZone

/** 노출 가능 시간대 판정. 웹 SDK와 같이 단말의 현지 시각으로 본다. */
internal class InAppWindowChecker(
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
) {
    fun check(window: SendableTimeWindow?): Boolean {
        if (window == null) return true
        val calendar = Calendar.getInstance(timeZone()).apply { timeInMillis = now() }
        val today = DAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val rule = window.weeklyTimeRules.firstOrNull { it.dayOfWeek == today } ?: return false
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return rule.timeRanges.any { minuteOfDay >= it.startMinuteOfDay && minuteOfDay <= it.endMinuteOfDay }
    }

    private companion object {
        val DAYS = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    }
}
