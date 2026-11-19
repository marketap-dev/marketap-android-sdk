package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import com.marketap.sdk.utils.listAdapter
import com.marketap.sdk.utils.longAdapter
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class GlobalImpressionEntry(
    val ts: Long,
    val tagIds: List<String>,
)

/**
 * [globalImpressionRetentionMs]보다 오래된 전체 빈도용 노출 기록은 읽고 쓸 때 지운다.
 * 이 기간이 적용 중인 어떤 규칙의 기간보다 짧으면 판정이 달라지므로, 규칙 최대 기간 이상이어야 한다.
 */
internal class CampaignExposureService(
    private val internalStorage: InternalStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val globalImpressionRetentionMs: () -> Long = { MIN_GLOBAL_IMPRESSION_RETENTION_MS },
) : CampaignExposing {
    private val globalImpressionsAdapter = listAdapter<GlobalImpressionEntry>()

    override fun recordGlobalImpression(tagIds: List<String>) {
        synchronized(this) {
            val log = readGlobalImpressions()
            internalStorage.setItem(GLOBAL_IMPRESSIONS_KEY, log + GlobalImpressionEntry(now(), tagIds), globalImpressionsAdapter)
        }
    }

    override fun hasReachedGlobalFrequencyCap(rule: GlobalFrequencyCapRule): Boolean {
        val log = synchronized(this) { readGlobalImpressions() }
        val cutoff = now() - rule.durationMinutes * 60_000L
        return log.count { it.ts >= cutoff && ruleTagsIntersect(rule.tagIds, it.tagIds) } >= rule.limit
    }

    private fun readGlobalImpressions(): List<GlobalImpressionEntry> {
        val log = internalStorage.getItem(GLOBAL_IMPRESSIONS_KEY, globalImpressionsAdapter).orEmpty()
        val cutoff = now() - globalImpressionRetentionMs()
        val kept = log.filter { it.ts >= cutoff }
        if (kept.size != log.size) internalStorage.setItem(GLOBAL_IMPRESSIONS_KEY, kept, globalImpressionsAdapter)
        return kept
    }

    override fun hideCampaign(campaignId: String, until: Long) {
        internalStorage.setItem("hide_campaign_${campaignId}", until, longAdapter)
    }

    override fun isCampaignHidden(campaignId: String): Boolean {
        val hiddenUntil =
            internalStorage.getItem("hide_campaign_${campaignId}", longAdapter)
        return hiddenUntil != null && hiddenUntil > System.currentTimeMillis()
    }

    override fun recordImpression(campaignId: String) {
        internalStorage.queueItem(
            "impressions_${campaignId}",
            System.currentTimeMillis(),
            longAdapter
        )
    }

    override fun hasReachedImpressionLimit(
        campaignId: String,
        windowMinutes: Int,
        maxCount: Int
    ): Boolean {
        val impressions =
            internalStorage.getItemList("impressions_${campaignId}", longAdapter)

        val now = System.currentTimeMillis()
        val windowStart = now - windowMinutes * 60 * 1000
        return impressions.count { it > windowStart } >= maxCount
    }

    companion object {
        private const val GLOBAL_IMPRESSIONS_KEY = "global_impressions"
        const val MIN_GLOBAL_IMPRESSION_RETENTION_MS = 365L * 24 * 60 * 60 * 1000

        fun retentionFor(rules: List<GlobalFrequencyCapRule>): Long =
            maxOf(MIN_GLOBAL_IMPRESSION_RETENTION_MS, rules.maxOfOrNull { it.durationMinutes * 60_000L } ?: 0L)
    }
}
