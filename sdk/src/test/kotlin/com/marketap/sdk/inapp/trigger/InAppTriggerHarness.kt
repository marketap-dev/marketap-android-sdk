package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.DelayedInAppCoordinator
import com.marketap.sdk.domain.service.inapp.InAppCallbackFactory
import com.marketap.sdk.domain.service.inapp.InAppScheduler
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.InAppTimerStore
import com.marketap.sdk.domain.service.inapp.condition.EventFlowEvaluator
import com.marketap.sdk.domain.service.inapp.condition.InAppWindowChecker
import com.marketap.sdk.domain.service.inapp.condition.OccurrenceConditionEvaluator
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import java.util.TimeZone

/** 저장소를 공유해 "앱 재실행"을 [restart]로 흉내 낸다. */
internal class InAppTriggerHarness(
    campaigns: List<InAppCampaign>,
    val storage: InMemoryStorage = InMemoryStorage(),
    val scheduler: FakeScheduler = FakeScheduler(),
) {
    val fetching = FakeFetching(campaigns)
    val exposing = FakeExposing()
    val view = FakeInAppView(autoShow = true)
    val backend = RecordingBackend()
    var foreground = true
    var displayable = true
    var userId: String? = "u1"
    var sessionId = "s1"
    val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    val evaluator = OccurrenceConditionEvaluator(storage) { scheduler.now }
    val flowEvaluator = EventFlowEvaluator(storage, MatchByEventName()) { scheduler.now }
    lateinit var coordinator: DelayedInAppCoordinator
        private set
    lateinit var service: InAppService
        private set

    init {
        build(scheduler)
    }

    fun build(executor: InAppScheduler) {
        coordinator = DelayedInAppCoordinator(
            storage = storage,
            scheduler = executor,
            conditionChecker = MatchByEventName(),
            canDisplay = { foreground && displayable },
            isForeground = { foreground },
            isSlotShowing = view::isShowing,
            now = { scheduler.now },
        )
        val factory = InAppCallbackFactory(
            backend = backend,
            projectId = { "p1" },
            userId = { userId },
            device = { DeviceReq(deviceId = "d") },
            sessionId = { sessionId },
            trackEvent = { _, _, _ -> },
            setUserProperties = {},
        )
        service = InAppService(
            exposing,
            MatchByEventName(),
            fetching,
            view,
            factory::create,
            evaluator,
            coordinator,
            executor,
            InAppWindowChecker({ scheduler.now }, { timeZone }),
            canDisplay = { foreground && displayable },
            timerStore = InAppTimerStore(storage) { scheduler.now },
            eventFlowEvaluator = flowEvaluator,
        )
    }

    fun restart() {
        scheduler.cancelAll()
        view.clear()
        build(scheduler)
        service.restoreDelayed()
    }

    fun fire(
        name: String = TestCampaigns.TRIGGER,
        origin: InAppOrigin? = null,
        generation: Long? = null,
    ) {
        val event = TestCampaigns.event(name, sessionId, userId)
        service.onEvent(event, origin, generation ?: service.currentGeneration())
    }

    fun changeUser(next: String?) = service.changeIdentity(fromAnonymous = userId == null) { userId = next }

    fun shownIds(): List<String> = view.shownHtml.map { it.removePrefix("<div>").removeSuffix("</div>") }

    fun impressionEvents() = backend.events.filter { it.name == "mkt_delivery_message" }
}
