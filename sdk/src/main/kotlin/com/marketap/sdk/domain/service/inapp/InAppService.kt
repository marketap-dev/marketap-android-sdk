package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.domain.service.inapp.condition.EventFlowEvaluating
import com.marketap.sdk.domain.service.inapp.condition.EventFlowEvaluation
import com.marketap.sdk.domain.service.inapp.condition.InAppWindowChecker
import com.marketap.sdk.domain.service.inapp.condition.OccurrenceEvaluating
import com.marketap.sdk.domain.service.inapp.condition.matchesAbort
import com.marketap.sdk.domain.service.inapp.condition.matchesTrigger
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.model.internal.slot
import com.marketap.sdk.utils.logger
import java.util.concurrent.atomic.AtomicLong

/**
 * 상세 fetch 를 실제로 보내는 후보의 최대 개수. 서버가 노출을 확정하지 못해 빈 응답을
 * 주면 다음 후보로 넘어가는데, 그때마다 요청이 나가므로 상한을 둔다. html 이 이미 있는
 * 정적 렌더 캠페인은 서버를 안 타므로 이 예산을 깎지 않는다. (web SDK 와 동일한 정책)
 */
private const val MAX_FALLTHROUGH_FETCHES = 5

/**
 * 폴스루 전체의 시간 예산(ms).
 *
 * resolveCampaignHtml 은 runBlocking + withTimeoutOrNull(1000) 이라 **실행 스레드를**
 * fetch 당 최대 1초 붙잡는다. 상한 없이 후보를 이어가면 최대 5초를 블록하므로 예산을 넘기면 멈춘다.
 * (web SDK 는 이 값이 2000ms 다. Android 는 fetch 당 1초 블록이라 2회면 예산이 찬다.)
 */
private const val FALLTHROUGH_BUDGET_MS = 2000L

private const val SESSION_ID_KEY = "mkt_session_id"

/**
 * 경과 시간 측정용 단조 시계(ms).
 *
 * 벽시계(System.currentTimeMillis)는 NTP 보정이나 사용자의 시간 변경으로 앞뒤로 튄다.
 * 뒤로 튀면 예산이 사실상 무한이 되고, 앞으로 튀면 첫 후보에서 바로 끊긴다. 기간을 재는 데는
 * 절대 시각이 아니라 단조 증가 값을 쓴다. (web SDK 의 performance.now 와 같은 이유)
 *
 * SystemClock.elapsedRealtime 이 아니라 System.nanoTime 을 쓴 이유: 이 파일은 도메인 계층이라
 * 안드로이드 프레임워크에 의존하지 않는다(순수 JVM 단위 테스트에서 그대로 돈다).
 */

internal fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000

/** 화면 전환 보호 시간(ms). [InAppService.screenChanged] 호출 전 이 시간 안에 화면에 나타난 메시지는 그 호출로 닫지 않는다. */
internal const val SCREEN_CHANGE_GRACE_MS = 100L

private object AlwaysSatisfiedOccurrence : OccurrenceEvaluating {
    override fun recordAndEvaluate(campaign: InAppCampaign, sessionId: String?) = true
    override fun reset(campaignId: String) {}
    override fun resetAll() {}
}

private object NoEventFlow : EventFlowEvaluating {
    override fun now() = System.currentTimeMillis()
    override fun evaluate(campaign: InAppCampaign, event: IngestEventRequest, arrivalMatched: Boolean, occurredAt: Long) =
        EventFlowEvaluation(arrivalMatched, restarted = false)
    override fun canShow(campaign: InAppCampaign) = true
    override fun clear(campaignId: String) {}
    override fun clearAll() {}
}

private val InAppCampaign.hasEventFlow: Boolean
    get() = triggerEventCondition.flow?.precedingSteps?.isNotEmpty() == true

/**
 * 판정 상태(지연 예약·누적 횟수)를 건드리는 작업은 모두 [scheduler]에서 하나씩 실행한다.
 * [delayedCoordinator]가 없으면 지연 설정을 무시하고 바로 띄운다.
 * [canDisplay]는 지금 resume된 화면이 있어 인앱을 띄울 수 있는지다.
 *
 * 사용자 세대(generation)는 userId가 바뀔 때마다 올라간다. 이전 세대에 받은 이벤트와 실행 중인 예약은 띄우지 않는다.
 * 단, 비회원(userId 없음) 이벤트는 이전 세대여도 이벤트 플로우 진행에는 쓴다. 비회원에서 로그인하면 진행을 이어 준다.
 */
internal class InAppService(
    private val campaignExposureService: CampaignExposing,
    private val eventConditionChecker: ConditionChecker,
    private val campaignFetchService: CampaignFetching,
    private val inAppView: InAppView,
    private val callbackFactory: () -> InAppCallbacks = { NoopInAppCallbacks },
    private val occurrenceEvaluator: OccurrenceEvaluating = AlwaysSatisfiedOccurrence,
    private val delayedCoordinator: DelayedInAppCoordinator? = null,
    private val scheduler: InAppScheduler = InlineInAppScheduler,
    private val windowChecker: InAppWindowChecker = InAppWindowChecker(),
    private val canDisplay: () -> Boolean = { true },
    private val timerStore: InAppTimerStore? = null,
    private val eventFlowEvaluator: EventFlowEvaluating = NoEventFlow,
    private val clock: () -> Long = ::monotonicNowMs,
) {
    private val identityLock = Any()
    private val identityGeneration = AtomicLong()

    /** 이벤트 플로우 진행을 지운 횟수. 비회원 이벤트는 그 사이 진행이 지워졌으면 플로우에 쓰지 않는다. */
    private val eventFlowClears = AtomicLong()

    init {
        delayedCoordinator?.bindExecutor(::executeReservation)
    }

    fun screenChanged() {
        inAppView.closeForAppScreenChange(clock() - SCREEN_CHANGE_GRACE_MS)
    }

    fun onEvent(
        event: IngestEventRequest,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        onEvent(event, origin = null, onImpression, onClick, onTrack, onSetUserProperties)
    }

    /** 이 이벤트로 즉시 뜨는 메시지에 [onImpression] 등 주어진 콜백을 쓴다. 지연 예약으로 뜨는 메시지는 [callbackFactory]로 만든다. */
    fun onEvent(
        event: IngestEventRequest,
        origin: InAppOrigin?,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        val callbacks = object : InAppCallbacks {
            override fun onImpression(campaign: InAppCampaign) = onImpression(campaign)
            override fun onClick(campaign: InAppCampaign, locationId: String) = onClick(campaign, locationId)
            override fun onTrack(campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) =
                onTrack(campaign, eventName, properties)
            override fun onSetUserProperties(properties: Map<String, Any>) = onSetUserProperties(properties)
        }
        handleEvent(event, origin, { callbacks }, currentGeneration())
    }

    fun currentGeneration(): Long = identityGeneration.get()

    /**
     * [origin]은 이벤트가 들어온 웹뷰다. 앱 화면 이벤트면 null.
     * [generation]은 이벤트의 userId를 읽기 전에 [currentGeneration]으로 얻은 값이다.
     */
    fun onEvent(
        event: IngestEventRequest,
        origin: InAppOrigin?,
        generation: Long = currentGeneration(),
    ) {
        handleEvent(event, origin, callbackFactory, generation)
    }

    /** SDK 초기화·포그라운드 복귀 때 저장된 예약의 타이머를 다시 걸고, 시각이 지난 예약을 실행한다. */
    fun restoreDelayed() {
        val coordinator = delayedCoordinator ?: return
        scheduler.execute { coordinator.sync() }
    }

    /**
     * userId 를 [next]로 바꾸는 [change]를 실행한다. 지금 userId([current])를 읽는 것부터 바꾸는 것까지 한 잠금 안에서 해서,
     * 동시에 들어온 identify·로그아웃이 이전 사용자를 잘못 읽고 폐기를 건너뛰지 않게 한다.
     */
    fun switchIdentity(next: String?, current: () -> String?, change: () -> Unit) {
        synchronized(identityLock) {
            val previous = current()
            if (previous == next) change() else changeIdentity(fromAnonymous = previous == null, change = change)
        }
    }

    /**
     * userId를 바꾸는 [change]를 세대 전환과 함께 실행하고, 지연 예약·누적 횟수·이벤트 플로우 진행을 바로 지운다.
     * [fromAnonymous]면(익명 → 로그인) 같은 사람으로 보고 모두 그대로 둔다(Braze changeUser 와 같다).
     */
    fun changeIdentity(fromAnonymous: Boolean = false, change: () -> Unit) {
        if (fromAnonymous) {
            synchronized(identityLock) { change() }
            return
        }
        synchronized(identityLock) {
            identityGeneration.incrementAndGet()
            try {
                change()
            } finally {
                eventFlowClears.incrementAndGet()
                delayedCoordinator?.clearPersisted()
                occurrenceEvaluator.resetAll()
                eventFlowEvaluator.clearAll()
                scheduler.execute {
                    delayedCoordinator?.clearAll()
                    occurrenceEvaluator.resetAll()
                    eventFlowEvaluator.clearAll()
                }
            }
        }
    }

    private fun isCurrent(generation: Long) = generation == identityGeneration.get()

    private fun handleEvent(
        event: IngestEventRequest,
        origin: InAppOrigin?,
        callbacks: () -> InAppCallbacks,
        generation: Long,
    ) {
        val occurredAt = eventFlowEvaluator.now()
        synchronized(identityLock) {
            val flowClears = eventFlowClears.get()
            if (!canAdvanceFlows(event, generation, flowClears)) return
            scheduler.execute { processEvent(event, origin, callbacks, generation, occurredAt, flowClears) }
        }
    }

    private fun canAdvanceFlows(event: IngestEventRequest, generation: Long, flowClears: Long) =
        isCurrent(generation) || (event.userId == null && flowClears == eventFlowClears.get())

    private fun processEvent(
        event: IngestEventRequest,
        origin: InAppOrigin?,
        callbacks: () -> InAppCallbacks,
        generation: Long,
        occurredAt: Long,
        flowClears: Long,
    ) {
        if (!canAdvanceFlows(event, generation, flowClears)) return
        if (isCurrent(generation)) delayedCoordinator?.cancelMatching(event)

        campaignFetchService.useCampaigns(
            onUnavailable = {
                val stored = campaignFetchService.lastCachedCampaigns().orEmpty()
                advanceFlows(stored, event, generation, occurredAt, flowClears)
                if (isCurrent(generation)) resetAborted(stored, event)
            }
        ) { campaigns ->
            val flowFired = advanceFlows(campaigns, event, generation, occurredAt, flowClears)
            if (!isCurrent(generation)) return@useCampaigns
            resetAborted(campaigns, event)

            val globalFrequencyCaps = campaignFetchService.getGlobalFrequencyCaps()
            val sessionId = event.properties?.get(SESSION_ID_KEY) as? String
            // 우선순위 순 후보 전체를 본다. 1순위의 상세 fetch 가 빈 응답이어도 뒤 후보가 뜰 수 있다.
            val candidates = campaigns
                .filter { isEligible(it, event, globalFrequencyCaps, flowFired) }
                .filter { occurrenceEvaluator.recordAndEvaluate(it, sessionId) }

            showCandidates(candidates, event, origin, callbacks, generation)
        }
    }

    /** 플로우 캠페인의 진행을 이 이벤트로 갱신하고, 이번 이벤트로 도달한 캠페인 id를 돌려준다. */
    private fun advanceFlows(
        campaigns: List<InAppCampaign>,
        event: IngestEventRequest,
        generation: Long,
        occurredAt: Long,
        flowClears: Long,
    ): Set<String> {
        if (!canAdvanceFlows(event, generation, flowClears)) return emptySet()
        val fired = mutableSetOf<String>()
        campaigns.filter { it.hasEventFlow }.forEach { campaign ->
            val arrivalMatched =
                eventConditionChecker.matchesTrigger(campaign.triggerEventCondition, event.name, event.properties)
            val evaluation = eventFlowEvaluator.evaluate(campaign, event, arrivalMatched, occurredAt)
            if (evaluation.restarted) occurrenceEvaluator.reset(campaign.id)
            if (evaluation.fired) fired.add(campaign.id)
        }
        return fired
    }

    private fun resetAborted(campaigns: List<InAppCampaign>, event: IngestEventRequest) {
        campaigns
            .filter { eventConditionChecker.matchesAbort(it.triggerEventCondition.abortConditions, event) }
            .forEach { occurrenceEvaluator.reset(it.id) }
    }

    private fun isEligible(
        campaign: InAppCampaign,
        event: IngestEventRequest,
        globalFrequencyCaps: List<GlobalFrequencyCapRule>,
        flowFired: Set<String>,
    ): Boolean {
        val trigger = campaign.triggerEventCondition
        val triggered = if (campaign.hasEventFlow) {
            campaign.id in flowFired
        } else {
            eventConditionChecker.matchesTrigger(trigger, event.name, event.properties)
        }
        if (!triggered) {
            logger.v { "Campaign ${campaign.id} does not match event condition for event ${event.name}" }
            return false
        }
        if (!windowChecker.check(trigger.sendableTimeWindow)) {
            logger.v { "Campaign ${campaign.id} is outside its sendable time window" }
            return false
        }
        if (campaignExposureService.isCampaignHidden(campaign.id)) {
            logger.v { "Campaign ${campaign.id} is hidden" }
            return false
        }
        if (isTimerExpired(campaign)) {
            logger.v { "Campaign ${campaign.id} timer has expired" }
            return false
        }
        if (delayedCoordinator?.isScheduled(campaign.id) == true) {
            logger.v { "Campaign ${campaign.id} is already scheduled" }
            return false
        }
        if (hasReachedFrequencyCap(campaign)) {
            logger.v { "Campaign ${campaign.id} has reached frequency cap limit" }
            return false
        }
        if (hasReachedGlobalFrequencyCap(campaign, globalFrequencyCaps)) {
            logger.v { "Campaign ${campaign.id} has reached a global frequency cap" }
            return false
        }
        logger.v { "Campaign ${campaign.id} matches event condition for event ${event.name}" }
        return true
    }

    private fun hasReachedFrequencyCap(campaign: InAppCampaign): Boolean {
        val frequencyCap = campaign.triggerEventCondition.frequencyCap ?: return false
        return campaignExposureService.hasReachedImpressionLimit(
            campaign.id,
            frequencyCap.durationMinutes,
            frequencyCap.limit
        )
    }

    private fun hasReachedGlobalFrequencyCap(
        campaign: InAppCampaign,
        rules: List<GlobalFrequencyCapRule>,
    ): Boolean {
        if (campaign.disableGlobalFrequencyCap == true) return false
        val tagIds = campaign.tagIds.orEmpty()
        return rules.any { rule ->
            ruleTagsIntersect(rule.tagIds, tagIds) && campaignExposureService.hasReachedGlobalFrequencyCap(rule)
        }
    }

    private fun showCandidates(
        candidates: List<InAppCampaign>,
        event: IngestEventRequest,
        origin: InAppOrigin?,
        callbacks: () -> InAppCallbacks,
        generation: Long,
    ) {
        val deadline = monotonicNowMs() + FALLTHROUGH_BUDGET_MS
        var fetches = 0
        val handledSlots = mutableSetOf<InAppDisplaySlot>()
        for (campaign in candidates) {
            if (!isCurrent(generation)) return
            val slot = campaign.slot
            if (slot in handledSlots) continue
            if (inAppView.isShowing(slot)) {
                handledSlots.add(slot)
                continue
            }

            // 지연 예약은 그 자리만 처리된 것으로 보고 다른 자리 후보는 계속 본다.
            if (delayedCoordinator != null && campaign.delayMillis() != null) {
                if (delayedCoordinator.schedule(campaign, event, origin)) {
                    occurrenceEvaluator.reset(campaign.id)
                }
                handledSlots.add(slot)
                continue
            }

            // html 이 비어 있는 후보만 상세 fetch 를 탄다(resolveCampaignHtml 의 분기와 같은 조건).
            // 정적 렌더 캠페인은 서버를 안 타므로 요청 예산에서 뺀다.
            val needsFetch = campaign.html == null
            if (needsFetch) {
                if (fetches >= MAX_FALLTHROUGH_FETCHES) {
                    logger.v { "Reached fetch budget ($MAX_FALLTHROUGH_FETCHES), stopping fallthrough" }
                    break
                }
                // fetch 는 스레드를 최대 1초 블록한다. 남은 예산이 없으면 시작조차 안 한다.
                if (monotonicNowMs() >= deadline) {
                    logger.v { "Reached time budget (${FALLTHROUGH_BUDGET_MS}ms), stopping fallthrough" }
                    break
                }
                fetches++
            }

            val resolvedCampaign = campaignFetchService.resolveCampaignHtml(campaign, event)
                ?.withTimerOf(campaign)
                ?: continue

            if (!isCurrent(generation)) return
            if (handleCampaign(resolvedCampaign, campaign, slot, origin, callbacks())) {
                handledSlots.add(slot)
            }
        }
    }

    /**
     * 실행 직전에 최신 목록으로 다시 확인한다. 목록을 받을 수 없으면 마지막 캐시로 보고,
     * 캐시도 없으면 [DelayedDelivery.RETRY]. [origin]은 예약을 만든 이벤트의 출처 웹뷰이고,
     * 이미 사라졌으면 출처 없이 띄운다.
     */
    private fun executeReservation(reservation: DelayedReservation, origin: InAppOrigin?): DelayedDelivery {
        val generation = currentGeneration()
        var campaigns: List<InAppCampaign>? = null
        campaignFetchService.useCampaigns(
            onUnavailable = { campaigns = campaignFetchService.lastCachedCampaigns() }
        ) { campaigns = it }
        val campaign = (campaigns ?: return DelayedDelivery.RETRY)
            .firstOrNull { it.id == reservation.campaignId }
            ?: return DelayedDelivery.DONE

        if (campaignExposureService.isCampaignHidden(campaign.id) ||
            hasReachedFrequencyCap(campaign) ||
            hasReachedGlobalFrequencyCap(campaign, campaignFetchService.getGlobalFrequencyCaps())
        ) {
            return DelayedDelivery.DONE
        }

        val slot = reservation.slot
        if (inAppView.isShowing(slot)) return DelayedDelivery.DONE
        val resolvedCampaign = campaignFetchService.resolveCampaignHtml(campaign, reservation.triggerEvent)
            ?.withTimerOf(campaign)
            ?: return DelayedDelivery.DONE
        if (!canDisplay()) return DelayedDelivery.RETRY
        if (inAppView.isShowing(slot) || !isCurrent(generation)) return DelayedDelivery.DONE

        handleCampaign(resolvedCampaign, campaign, slot, origin, callbackFactory())
        return DelayedDelivery.DONE
    }

    /** [listed]는 목록의 캠페인이다. 상세 응답에는 플로우 설정이 없을 수 있어 진행 확인은 목록 기준으로 한다. */
    private fun handleCampaign(
        targetCampaign: InAppCampaign,
        listed: InAppCampaign,
        slot: InAppDisplaySlot,
        origin: InAppOrigin?,
        callbacks: InAppCallbacks,
    ): Boolean {
        logger.d { "Showing in-app campaign: ${targetCampaign.id} with layout type: ${targetCampaign.layout.layoutType}" }
        val campaignHtml = targetCampaign.html ?: return false
        if (isTimerExpired(targetCampaign)) {
            logger.d { "Campaign ${targetCampaign.id} timer has expired" }
            return false
        }
        if (!eventFlowEvaluator.canShow(listed)) {
            logger.d { "Campaign ${targetCampaign.id} event flow is no longer in progress" }
            return false
        }
        val closeOnPageLeave = InAppPageScope.closesOnPageLeave(slot, targetCampaign.floatingScope)
        val shown = showCampaign(targetCampaign, listed, campaignHtml, slot, origin, closeOnPageLeave, callbacks)
        if (shown) {
            occurrenceEvaluator.reset(targetCampaign.id)
        }
        return shown
    }

    private fun showCampaign(
        targetCampaign: InAppCampaign,
        listed: InAppCampaign,
        campaignHtml: String,
        slot: InAppDisplaySlot,
        origin: InAppOrigin?,
        closeOnPageLeave: Boolean,
        callbacks: InAppCallbacks,
    ): Boolean {
        val liveOrigin = origin?.takeUnless { it.isGone }
        if (origin != null && liveOrigin == null) {
            logger.d { "Origin web view of campaign ${targetCampaign.id} is gone; showing it as an app message" }
        }
        return inAppView.show(
            campaignHtml,
            slot,
            liveOrigin,
            closeOnPageLeave,
            {
                campaignExposureService.recordImpression(targetCampaign.id)
                campaignExposureService.recordGlobalImpression(targetCampaign.tagIds.orEmpty())
                scheduler.execute { eventFlowEvaluator.clear(targetCampaign.id) }
                callbacks.onImpression(targetCampaign)
                logger.d { "Recorded impression for campaign: ${targetCampaign.id}" }
            },
            { locationId ->
                callbacks.onClick(targetCampaign, locationId)
                logger.d { "Recorded click for campaign: ${targetCampaign.id} at location: $locationId" }
                targetCampaign.id
            },
            { hideType ->
                logger.d { "Hiding campaign: ${targetCampaign.id} with hide type: $hideType" }
                hideCampaignByType(targetCampaign.id, hideType)
            },
            { eventName, properties ->
                callbacks.onTrack(targetCampaign, eventName, properties)
            },
            callbacks::onSetUserProperties,
            { timerStore?.startFor(targetCampaign) ?: System.currentTimeMillis() },
            { !isTimerExpired(targetCampaign) && eventFlowEvaluator.canShow(listed) },
        )
    }

    private fun isTimerExpired(campaign: InAppCampaign): Boolean = timerStore?.isExpired(campaign) == true

    private fun InAppCampaign.withTimerOf(listed: InAppCampaign): InAppCampaign =
        if (this === listed) this else copy(timer = timer ?: listed.timer, isTest = isTest ?: listed.isTest)

    /**
     * 캠페인 숨김 처리 (웹브릿지에서 호출)
     */
    fun hideCampaign(campaignId: String, hideType: HideType) {
        logger.d { "Hiding campaign from web bridge: $campaignId with hide type: $hideType" }
        hideCampaignByType(campaignId, hideType)
    }

    private fun hideCampaignByType(campaignId: String, hideType: HideType) {
        when (hideType) {
            HideType.HIDE_FOR_ONE_DAY -> campaignExposureService.hideCampaign(
                campaignId, System.currentTimeMillis() + 1000 * 60 * 60 * 24
            )

            HideType.HIDE_FOR_SEVEN_DAYS -> campaignExposureService.hideCampaign(
                campaignId, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7
            )

            HideType.HIDE_FOREVER -> campaignExposureService.hideCampaign(
                campaignId,
                System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10
            )

            HideType.CLOSE -> {}
        }
    }
}
