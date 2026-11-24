package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.utils.mapAdapter
import com.squareup.moshi.JsonClass

/**
 * [fired]: 이 이벤트로 트리거된다. 플로우가 없는 캠페인은 트리거 조건 일치 여부 그대로다.
 * [restarted]: 플로우가 진입·취소로 새로 시작되거나 버려졌다.
 */
internal data class EventFlowEvaluation(val fired: Boolean, val restarted: Boolean)

internal interface EventFlowEvaluating {
    /** 이벤트가 일어난 시각(epoch ms). 목록을 기다린 뒤 판정해도 이 시각으로 잰다. */
    fun now(): Long

    fun evaluate(
        campaign: InAppCampaign,
        event: IngestEventRequest,
        arrivalMatched: Boolean,
        arrivalCounted: Boolean,
        occurredAt: Long,
    ): EventFlowEvaluation

    /** 노출 직전에 부른다. 도달 뒤 기다리는 사이 진행이 버려졌거나 재진입이 막혔으면 false. 플로우가 없으면 항상 true. */
    fun canShow(campaign: InAppCampaign): Boolean

    /** 인앱이 실제로 노출됐을 때 부른다. 다시 뜨려면 진입부터 다시 해야 한다. */
    fun clear(campaignId: String)

    /** 로그아웃하거나 다른 회원으로 바뀌었을 때 부른다. */
    fun clearAll()
}

@JsonClass(generateAdapter = true)
internal data class StoredEventFlowState(
    val nextStep: Int,
    val lastStepAt: Long,
    val blockedUntil: Long? = null,
    val version: String,
)

/** 진행 상태 보관 기간. 플로우 단계 제한 시간 상한(31일)보다 길게 잡는다. */
private const val MAX_STATE_AGE_MS = 32L * 24 * 60 * 60 * 1000

internal class EventFlowEvaluator(
    private val storage: InternalStorage,
    private val conditionChecker: ConditionChecker,
    private val currentTime: () -> Long = System::currentTimeMillis,
) : EventFlowEvaluating {
    private val statesAdapter = mapAdapter<String, StoredEventFlowState>()

    override fun now(): Long = currentTime()

    override fun evaluate(
        campaign: InAppCampaign,
        event: IngestEventRequest,
        arrivalMatched: Boolean,
        arrivalCounted: Boolean,
        occurredAt: Long,
    ): EventFlowEvaluation {
        val trigger = campaign.triggerEventCondition
        val flow = trigger.flow?.takeIf { it.precedingSteps.isNotEmpty() }
            ?: return EventFlowEvaluation(arrivalMatched, restarted = false)

        val states = load()
        val version = campaign.triggerConditionVersion.orEmpty()
        val current = states[campaign.id]
            ?.takeIf { it.version == version }
            ?.let { EventFlowState(it.nextStep, it.lastStepAt, it.blockedUntil) }

        val transition = EventFlowRule.next(
            current,
            flow,
            arrivalMatched,
            arrivalCounted,
            trigger.abortConditions.orEmpty(),
            occurredAt,
        ) { conditionChecker.checkCondition(it, event.name, event.properties) }

        val next = transition.state
        if (next != null) {
            states[campaign.id] = StoredEventFlowState(next.nextStep, next.lastStepAt, next.blockedUntil, version)
            save(states)
        } else if (current != null) {
            // 설정 버전이 다른 진행은 지우지 않는다. 옛 설정의 목록으로 판정해도 새 설정의 진행이 남는다.
            states.remove(campaign.id)
            save(states)
        }
        return EventFlowEvaluation(transition.fired, transition.restarted)
    }

    override fun canShow(campaign: InAppCampaign): Boolean {
        val flow = campaign.triggerEventCondition.flow
        if (flow == null || flow.precedingSteps.isEmpty()) return true
        val stored = load()[campaign.id] ?: return false
        return stored.version == campaign.triggerConditionVersion.orEmpty() &&
                stored.nextStep >= 1 &&
                stored.blockedUntil == null
    }

    override fun clear(campaignId: String) {
        val states = load()
        if (states.remove(campaignId) != null) save(states)
    }

    override fun clearAll() {
        storage.removeItem(STORAGE_KEY)
    }

    private fun load(): MutableMap<String, StoredEventFlowState> = try {
        storage.getItem(STORAGE_KEY, statesAdapter).orEmpty().toMutableMap()
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save(states: Map<String, StoredEventFlowState>) {
        val cutoff = currentTime() - MAX_STATE_AGE_MS
        val kept = states.filterValues { it.lastStepAt >= cutoff }
        if (kept.isEmpty()) storage.removeItem(STORAGE_KEY) else storage.setItem(STORAGE_KEY, kept, statesAdapter)
    }

    private companion object {
        const val STORAGE_KEY = "inapp_event_flow_states"
    }
}
