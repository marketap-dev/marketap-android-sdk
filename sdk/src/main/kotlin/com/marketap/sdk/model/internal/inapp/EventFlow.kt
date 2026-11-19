package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

/**
 * 트리거(도달) 전에 차례로 일어나야 하는 이벤트 단계. 첫 단계가 진입이다.
 * [reentryBlockSeconds]가 있으면 진행 중 취소 이벤트가 일어난 뒤 그 시간 동안 다시 진입하지 않는다.
 */
@JsonClass(generateAdapter = true)
data class EventFlow(
    val precedingSteps: List<EventFlowStep>,
    val reentryBlockSeconds: Long? = null,
)

/** [conditions] 중 하나가 직전 단계로부터 [withinSeconds] 안에 일어나면 이 단계를 지난다. 진입 단계면 다음 단계의 제한 시간이다. */
@JsonClass(generateAdapter = true)
data class EventFlowStep(
    val conditions: List<Condition>,
    val withinSeconds: Long,
)
