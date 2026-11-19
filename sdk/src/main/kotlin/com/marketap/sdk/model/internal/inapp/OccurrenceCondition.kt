package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

/** 트리거 이벤트가 [count]번 쌓여야 노출한다. [durationMinutes]가 있으면 그 기간 안의 횟수만 센다. */
@JsonClass(generateAdapter = true)
data class OccurrenceCondition(
    val count: Int,
    val durationMinutes: Int? = null,
    val sessionScoped: Boolean = false,
)
