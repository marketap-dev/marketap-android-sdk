package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SendableTimeWindow(
    val weeklyTimeRules: List<WeeklyTimeRule>,
    val sendableTimePolicy: String? = null,
    val timezone: String? = null,
)

/** [dayOfWeek]는 `MONDAY`…`SUNDAY`. */
@JsonClass(generateAdapter = true)
data class WeeklyTimeRule(
    val dayOfWeek: String,
    val timeRanges: List<TimeRange>,
)

/** 하루의 분(0~1439), 양 끝 포함. */
@JsonClass(generateAdapter = true)
data class TimeRange(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)
