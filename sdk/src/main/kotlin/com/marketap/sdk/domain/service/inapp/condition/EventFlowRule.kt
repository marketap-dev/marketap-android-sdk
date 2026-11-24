package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFlow

/**
 * 한 캠페인의 플로우 진행 위치.
 * [nextStep]: 기다리는 단계 번호(1부터). precedingSteps 크기와 같으면 도달을 기다린다.
 * [lastStepAt]: 직전 단계를 일으킨 이벤트의 발생 시각(epoch ms). 재진입 제한 중이면 취소 이벤트의 발생 시각.
 * [blockedUntil]: 이 시각(epoch ms) 전까지 진입하지 않는다. 이때 [nextStep]은 0이다.
 */
internal data class EventFlowState(
    val nextStep: Int,
    val lastStepAt: Long,
    val blockedUntil: Long? = null,
)

/**
 * [fired]: 도달했다. 이후 처리는 일반 트리거와 같다.
 * [restarted]: 진입 또는 취소로 진행이 새로 시작되거나 버려졌다. 트리거 횟수 조건은 이때 초기화한다.
 */
internal data class EventFlowTransition(
    val state: EventFlowState?,
    val fired: Boolean,
    val restarted: Boolean,
)

/**
 * 이벤트 플로우 판정 규칙. 웹 SDK·서버와 같은 규칙이며 같은 사례 파일(`event-flow-cases.json`)로 검증한다.
 *
 * 인앱이 실제로 노출되면 호출하는 쪽이 상태를 지운다. 도달했지만 노출되지 않았으면 상태를 유지한다.
 * 진입 직후 단계에서 진입 이벤트가 다시 오면 시각만 갱신하고, 그보다 더 진행했거나 도달을 1회 이상 셌으면 진입 이벤트를 무시한다.
 * 취소와 진입에 모두 맞는 이벤트는 진행을 버린 뒤 새로 진입한다.
 * 재진입 제한 시간이 있으면 진행 기록(만료됐지만 노출되지 않은 것 포함)이 있을 때 일어난 취소 이벤트만 그 시간 동안 진입을 막는다.
 */
internal object EventFlowRule {

    /**
     * @param arrivalMatched 이 이벤트가 캠페인의 트리거 조건(도달)에 맞는지.
     * @param arrivalCounted 트리거 횟수 조건이 있고 지금 진행에서 도달을 1회 이상 셌는지. 횟수는 호출하는 쪽이 센다.
     * @param cancelConditions 트리거의 취소 이벤트(abortConditions).
     */
    fun next(
        state: EventFlowState?,
        flow: EventFlow,
        arrivalMatched: Boolean,
        arrivalCounted: Boolean,
        cancelConditions: List<Condition>,
        eventAt: Long,
        matches: (Condition) -> Boolean,
    ): EventFlowTransition {
        val steps = flow.precedingSteps
        if (steps.isEmpty()) return EventFlowTransition(null, fired = false, restarted = false)

        if (cancelConditions.any(matches)) {
            if (state?.blockedUntil != null && eventAt < state.blockedUntil) {
                return EventFlowTransition(state, fired = false, restarted = false)
            }
            val hasProgress = state != null && state.blockedUntil == null && state.nextStep >= 1
            val blocked = flow.reentryBlockSeconds?.takeIf { hasProgress }?.let {
                EventFlowState(0, eventAt, blockedUntil = eventAt + it * 1000)
            }
            val reentered = EventFlowState(1, eventAt).takeIf { steps.first().conditions.any(matches) }
            return EventFlowTransition(blocked ?: reentered, fired = false, restarted = true)
        }

        if (state?.blockedUntil != null && eventAt < state.blockedUntil) {
            return EventFlowTransition(state, fired = false, restarted = false)
        }

        val alive = state?.takeIf { it.isAliveAt(eventAt, flow) }
        if (alive != null) {
            val reachesArrival = alive.nextStep == steps.size
            val waitingMatched = if (reachesArrival) arrivalMatched else steps[alive.nextStep].conditions.any(matches)
            if (waitingMatched) {
                return if (reachesArrival) {
                    EventFlowTransition(alive, fired = true, restarted = false)
                } else {
                    EventFlowTransition(EventFlowState(alive.nextStep + 1, eventAt), fired = false, restarted = false)
                }
            }
        }

        val progressedBeyondEntry = alive != null && (alive.nextStep > 1 || arrivalCounted)
        if (!progressedBeyondEntry && steps.first().conditions.any(matches)) {
            return EventFlowTransition(EventFlowState(1, eventAt), fired = false, restarted = true)
        }

        return EventFlowTransition(alive, fired = false, restarted = false)
    }

    private fun EventFlowState.isAliveAt(eventAt: Long, flow: EventFlow): Boolean {
        val step = flow.precedingSteps.getOrNull(nextStep - 1) ?: return false
        return eventAt <= lastStepAt + step.withinSeconds * 1000
    }
}
