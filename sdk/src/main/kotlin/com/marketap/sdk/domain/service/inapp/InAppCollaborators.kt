package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest

/**
 * InAppService 가 실제로 쓰는 것만 뽑은 seam.
 *
 * 구현체(CampaignFetchService)는 InternalStorage·MarketapBackend·ClientStateManager·
 * DeviceManager 를 생성자로 받아서, 테스트에서 대체할 방법이 없었다(그래서 이 파일의
 * 폴스루 로직이 통째로 미검증이었다). 쓰는 표면만 인터페이스로 좁혀 수기 fake 를 끼운다.
 */
internal interface CampaignFetching {
    fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit)
    fun resolveCampaignHtml(campaign: InAppCampaign, event: IngestEventRequest): InAppCampaign?
}

/** 노출 기록/숨김/빈도수 판단. 위와 같은 이유로 좁힌 seam. */
internal interface CampaignExposing {
    fun isCampaignHidden(campaignId: String): Boolean
    fun hasReachedImpressionLimit(campaignId: String, windowMinutes: Int, maxCount: Int): Boolean
    fun recordImpression(campaignId: String)
    fun hideCampaign(campaignId: String, until: Long)
}
