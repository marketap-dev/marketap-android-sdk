package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.utils.longAdapter

/** 타이머 캠페인의 최초 노출 시각(epoch ms)을 캠페인·revision 별로 저장한다. 테스트 응답([InAppCampaign.isTest])은 기록하지 않는다. */
internal class InAppTimerStore(
    private val storage: InternalStorage,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun isExpired(campaign: InAppCampaign): Boolean {
        val timer = campaign.timer ?: return false
        if (campaign.isTest == true) return false
        val start = getStart(campaign.id, timer.revision) ?: return false
        return now() >= start + timer.minutes * 60_000L
    }

    /** 템플릿에 넘길 타이머 시작 시각. 기록이 없을 때만 지금으로 기록한다. */
    fun startFor(campaign: InAppCampaign): Long {
        val now = now()
        val timer = campaign.timer ?: return now
        if (campaign.isTest == true) return now
        getStart(campaign.id, timer.revision)?.let { return it }
        storage.setItem(key(campaign.id, timer.revision), now, longAdapter)
        return now
    }

    private fun getStart(campaignId: String, revision: String): Long? = try {
        storage.getItem(key(campaignId, revision), longAdapter)
    } catch (e: Exception) {
        null
    }

    private fun key(campaignId: String, revision: String) = "timer_${campaignId}_$revision"
}
