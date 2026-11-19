package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.listAdapter
import com.squareup.moshi.JsonClass

internal interface OccurrenceEvaluating {
    /** 이번 이벤트를 기록하고 횟수 조건을 채웠는지 돌려준다. 조건이 없으면 항상 true. */
    fun recordAndEvaluate(campaign: InAppCampaign, sessionId: String?): Boolean
    fun reset(campaignId: String)
    fun resetAll()
}

@JsonClass(generateAdapter = true)
internal data class NonWindowedOccurrenceState(
    val validityKey: String,
    val lastSessionId: String? = null,
    val sessionCount: Int,
    val totalCount: Int,
)

@JsonClass(generateAdapter = true)
internal data class OccurrenceRecord(
    val timestamp: Long,
    val sessionId: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class WindowedOccurrenceState(
    val validityKey: String,
    val occurrences: List<OccurrenceRecord>,
)

internal class OccurrenceConditionEvaluator(
    private val storage: InternalStorage,
    private val now: () -> Long = System::currentTimeMillis,
) : OccurrenceEvaluating {
    private val nonWindowedAdapter = adapter<NonWindowedOccurrenceState>()
    private val windowedAdapter = adapter<WindowedOccurrenceState>()
    private val idsAdapter = listAdapter<String>()

    override fun recordAndEvaluate(campaign: InAppCampaign, sessionId: String?): Boolean {
        val condition = campaign.triggerEventCondition.occurrenceCondition ?: return true
        val validityKey = campaign.triggerConditionVersion.orEmpty()
        trackId(campaign.id)
        return if (condition.durationMinutes != null) {
            recordWindowed(campaign.id, condition, validityKey, sessionId)
        } else {
            recordNonWindowed(campaign.id, condition, validityKey, sessionId)
        }
    }

    override fun reset(campaignId: String) {
        storage.removeItem(nonWindowedKey(campaignId))
        storage.removeItem(windowedKey(campaignId))
    }

    override fun resetAll() {
        storage.getItem(IDS_KEY, idsAdapter).orEmpty().forEach(::reset)
        storage.removeItem(IDS_KEY)
    }

    private fun recordNonWindowed(
        campaignId: String,
        condition: OccurrenceCondition,
        validityKey: String,
        sessionId: String?,
    ): Boolean {
        val current = storage.getItem(nonWindowedKey(campaignId), nonWindowedAdapter)
        val base = if (current?.validityKey == validityKey) {
            current
        } else {
            storage.removeItem(windowedKey(campaignId))
            NonWindowedOccurrenceState(validityKey, sessionCount = 0, totalCount = 0)
        }
        val next = NonWindowedOccurrenceState(
            validityKey = validityKey,
            lastSessionId = sessionId,
            sessionCount = if (base.lastSessionId == sessionId) base.sessionCount + 1 else 1,
            totalCount = base.totalCount + 1,
        )
        storage.setItem(nonWindowedKey(campaignId), next, nonWindowedAdapter)
        val counted = if (condition.sessionScoped) next.sessionCount else next.totalCount
        return counted >= condition.count
    }

    private fun recordWindowed(
        campaignId: String,
        condition: OccurrenceCondition,
        validityKey: String,
        sessionId: String?,
    ): Boolean {
        val currentTime = now()
        val current = storage.getItem(windowedKey(campaignId), windowedAdapter)
        val base = if (current?.validityKey == validityKey) {
            current
        } else {
            storage.removeItem(nonWindowedKey(campaignId))
            WindowedOccurrenceState(validityKey, emptyList())
        }
        val cutoff = currentTime - condition.durationMinutes!! * 60_000L
        val occurrences = base.occurrences.filter { it.timestamp >= cutoff } +
                OccurrenceRecord(currentTime, sessionId)
        storage.setItem(windowedKey(campaignId), WindowedOccurrenceState(validityKey, occurrences), windowedAdapter)
        val counted = if (condition.sessionScoped) {
            occurrences.count { it.sessionId == sessionId }
        } else {
            occurrences.size
        }
        return counted >= condition.count
    }

    private fun trackId(campaignId: String) {
        val ids = storage.getItem(IDS_KEY, idsAdapter).orEmpty()
        if (campaignId !in ids) storage.setItem(IDS_KEY, ids + campaignId, idsAdapter)
    }

    private fun nonWindowedKey(campaignId: String) = "inapp_trigger_occurrence_non_windowed:$campaignId"
    private fun windowedKey(campaignId: String) = "inapp_trigger_occurrence_windowed:$campaignId"

    private companion object {
        const val IDS_KEY = "inapp_trigger_occurrence_campaign_ids"
    }
}
