package com.marketap.sdk.inapp.trigger

import android.app.Activity
import android.content.Context
import com.marketap.sdk.client.inapp.InAppSession
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.inapp.CampaignExposing
import com.marketap.sdk.domain.service.inapp.CampaignFetching
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.FetchCampaignsReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.InAppCampaignSingleRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventFlow
import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.model.internal.inapp.FrequencyCap
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppCampaignTimer
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.model.internal.inapp.Layout
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import com.marketap.sdk.model.internal.inapp.SendableTimeWindow
import java.util.Collections

internal object TestCampaigns {
    const val TRIGGER = "mkt_home_view"
    const val ABORT = "mkt_purchase"

    fun campaign(
        id: String,
        html: String? = "<div>$id</div>",
        slot: InAppDisplaySlot = InAppDisplaySlot.POPUP,
        delaySeconds: Int? = null,
        occurrence: OccurrenceCondition? = null,
        abortEvents: List<String>? = null,
        frequencyCap: FrequencyCap? = null,
        window: SendableTimeWindow? = null,
        tagIds: List<String>? = null,
        disableGlobalFrequencyCap: Boolean? = null,
        layoutSubType: String = "IMAGE",
        trigger: String = TRIGGER,
        anyOf: List<String>? = null,
        timer: InAppCampaignTimer? = null,
        isTest: Boolean? = null,
        flow: EventFlow? = null,
        version: String = "v1",
    ) = InAppCampaign(
        id = id,
        layout = Layout("MODAL", layoutSubType, emptyList()),
        triggerEventCondition = EventTriggerCondition(
            condition = Condition(EventFilter(trigger)),
            conditions = anyOf?.map { Condition(EventFilter(it)) },
            frequencyCap = frequencyCap,
            delaySeconds = delaySeconds,
            occurrenceCondition = occurrence,
            abortConditions = abortEvents?.map { Condition(EventFilter(it)) },
            sendableTimeWindow = window,
            flow = flow,
        ),
        priority = "NORMAL",
        html = html,
        updatedAt = "2026-11-09T00:00:00Z",
        displaySlot = if (slot == InAppDisplaySlot.FLOATING) "FLOATING" else null,
        triggerConditionVersion = version,
        tagIds = tagIds,
        disableGlobalFrequencyCap = disableGlobalFrequencyCap,
        timer = timer,
        isTest = isTest,
    )

    fun event(name: String = TRIGGER, sessionId: String = "s1", userId: String? = "u1") = IngestEventRequest(
        "e-$name", name, userId, DeviceReq(deviceId = "d"), mapOf("mkt_session_id" to sessionId)
    )
}

internal class MatchByEventName : ConditionChecker {
    override fun checkCondition(condition: Condition, eventName: String, eventProperty: Map<String, Any>?) =
        condition.eventFilter.eventName == eventName
}

internal class FakeFetching(
    var campaigns: List<InAppCampaign>,
    var resolved: Map<String, InAppCampaign> = emptyMap(),
) : CampaignFetching {
    var available = true
    var beforeDeliver: (() -> Unit)? = null
    var cached: List<InAppCampaign>? = null
    var globalCaps: List<GlobalFrequencyCapRule> = emptyList()
    var beforeResolve: (() -> Unit)? = null
    val resolveCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val resolveThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) = useCampaigns({}, block)

    override fun useCampaigns(onUnavailable: () -> Unit, block: (campaigns: List<InAppCampaign>) -> Unit) {
        beforeDeliver?.let {
            beforeDeliver = null
            it()
        }
        if (available) block(campaigns) else onUnavailable()
    }

    override fun lastCachedCampaigns(): List<InAppCampaign>? = cached

    override fun getGlobalFrequencyCaps(): List<GlobalFrequencyCapRule> = globalCaps

    override fun resolveCampaignHtml(campaign: InAppCampaign, event: IngestEventRequest): InAppCampaign? {
        if (campaign.html != null) return campaign
        beforeResolve?.let {
            beforeResolve = null
            it()
        }
        resolveCalls.add(campaign.id)
        resolveThreads.add(Thread.currentThread().name)
        return resolved[campaign.id]
    }
}

internal class FakeExposing(
    val hidden: MutableSet<String> = mutableSetOf(),
    val capped: MutableSet<String> = mutableSetOf(),
) : CampaignExposing {
    val impressions: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val globalImpressions: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
    var globalCapReached: (GlobalFrequencyCapRule) -> Boolean = { false }

    override fun isCampaignHidden(campaignId: String) = campaignId in hidden
    override fun hasReachedImpressionLimit(campaignId: String, windowMinutes: Int, maxCount: Int) = campaignId in capped
    override fun recordImpression(campaignId: String) {
        impressions.add(campaignId)
    }

    override fun hideCampaign(campaignId: String, until: Long) {
        hidden.add(campaignId)
    }

    override fun hasReachedGlobalFrequencyCap(rule: GlobalFrequencyCapRule) = globalCapReached(rule)
    override fun recordGlobalImpression(tagIds: List<String>) {
        globalImpressions.add(tagIds)
    }
}

/** 자리마다 하나씩 차지한다. [autoShow]면 표시하자마자 노출 콜백을 부른다. */
internal class FakeInAppView(private val autoShow: Boolean = false) : InAppView {
    private val occupied = mutableMapOf<InAppDisplaySlot, InAppSession>()
    val shownHtml: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val sessions: MutableList<InAppSession> = Collections.synchronizedList(mutableListOf())
    val origins: MutableList<InAppOrigin?> = Collections.synchronizedList(mutableListOf())
    val timerStarts: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    val mountChecks: MutableList<() -> Boolean> = Collections.synchronizedList(mutableListOf())

    @Synchronized
    override fun isShowing(slot: InAppDisplaySlot) = occupied.containsKey(slot)

    @Synchronized
    override fun show(
        html: String,
        slot: InAppDisplaySlot,
        origin: InAppOrigin?,
        closeOnPageLeave: Boolean,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
        timerStartAt: () -> Long,
        canMount: () -> Boolean,
    ): Boolean {
        if (occupied.containsKey(slot)) return false
        val session = InAppSession(onShow, onClick, onHide, onTrack, onSetUserProperties, origin, closeOnPageLeave)
        occupied[slot] = session
        shownHtml.add(html)
        origins.add(origin)
        timerStarts.add(timerStartAt())
        mountChecks.add(canMount)
        sessions.add(session)
        if (autoShow) session.shown()
        return true
    }

    @Synchronized
    fun occupy(slot: InAppDisplaySlot) {
        occupied[slot] = InAppSession({}, { "" }, {}, { _, _ -> }, {})
    }

    @Synchronized
    fun clear() = occupied.clear()
}

internal class RecordingBackend : MarketapBackend {
    val events: MutableList<IngestEventRequest> = Collections.synchronizedList(mutableListOf())

    override fun updateDevice(projectId: String, request: DeviceReq) {}
    override fun fetchCampaigns(
        request: FetchCampaignsReq,
        inTimeout: ((InAppCampaignRes) -> Unit)?,
        onSuccess: (InAppCampaignRes) -> Unit
    ) {}

    override fun fetchCampaign(
        campaignId: String,
        request: FetchCampaignReq,
        inTimeout: ((InAppCampaignSingleRes) -> Unit)?,
        onSuccess: (InAppCampaignSingleRes) -> Unit
    ) {}

    override fun track(projectId: String, request: IngestEventRequest) {
        events.add(request)
    }

    override fun updateProfile(projectId: String, request: UpdateProfileRequest) {}
}

internal class FakeDeviceManager : DeviceManager {
    override fun isDeviceReady() = true
    override fun setFirstOpen() = false
    override fun getDevice() = Device(appLocalId = "local")
    override fun setToken(token: String) {}
    override fun setDeviceOptIn(optIn: Boolean?) {}
    override fun setAppSetId(appSetId: String) {}
    override fun setGoogleAdvertisingId(gaid: String) {}
    override fun requestAuthorizationForPushNotifications(activity: Activity) {}
    override fun isPushNotificationEnabled(context: Context) = false
}

internal class FakeOrigin : InAppOrigin {
    @Volatile
    var gone = false

    override val isGone: Boolean get() = gone
    override fun canOpenUrl() = !gone
    override fun openUrl(url: String) {}
}
