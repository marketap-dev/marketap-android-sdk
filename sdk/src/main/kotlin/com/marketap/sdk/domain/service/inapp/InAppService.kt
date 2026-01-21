package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.MarketapWebBridge
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.utils.logger
import java.util.UUID

internal class InAppService(
    private val campaignExposureService: CampaignExposureService,
    private val eventConditionChecker: ConditionChecker,
    private val campaignFetchService: CampaignFetchService,
    private val inAppView: InAppView
) {

    fun onEvent(
        event: IngestEventRequest,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        onEvent(event, fromWebBridge = false, onImpression, onClick, onTrack, onSetUserProperties)
    }

    fun onEvent(
        event: IngestEventRequest,
        fromWebBridge: Boolean,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        campaignFetchService.useCampaigns { campaigns ->
            val targetCampaign = campaigns.find { campaign ->
                if (!eventConditionChecker.checkCondition(
                        campaign.triggerEventCondition.condition,
                        event.name,
                        event.properties
                    )
                ) {
                    logger.v {
                        "Campaign ${campaign.id} does not match event condition for event ${event.name}"
                    }
                    return@find false
                }
                if (campaignExposureService.isCampaignHidden(campaign.id)) {
                    logger.v { "Campaign ${campaign.id} is hidden" }
                    return@find false
                }

                campaign.triggerEventCondition.frequencyCap?.let { frequencyCap ->
                    if (campaignExposureService.hasReachedImpressionLimit(
                            campaign.id,
                            frequencyCap.durationMinutes,
                            frequencyCap.limit
                        )
                    ) {
                        logger.v {
                            "Campaign ${campaign.id} has reached frequency cap limit"
                        }
                        return@find false
                    }
                }
                logger.v { "Campaign ${campaign.id} matches event condition for event ${event.name}" }
                true
            }

            targetCampaign?.let {
                // 웹브릿지에서 온 이벤트이고 활성 웹브릿지가 있으면 웹으로 캠페인 전달
                val shouldDelegateToWeb = fromWebBridge && MarketapWebBridge.hasActiveWebBridge()

                if (shouldDelegateToWeb) {
                    handleCampaignForWeb(it)
                } else {
                    handleCampaign(it, event, onImpression, onClick, onTrack, onSetUserProperties)
                }
            }
        }
    }

    private fun handleCampaignForWeb(
        targetCampaign: InAppCampaign
    ) {
        logger.d { "Delegating in-app campaign to web: ${targetCampaign.id} with layout type: ${targetCampaign.layout.layoutType}" }

        // 빈도 제한을 위한 노출 기록 (이벤트 전송은 웹에서 impression이 올 때 수행)
        campaignExposureService.recordImpression(targetCampaign.id)

        // 웹으로 캠페인 전달
        val messageId = UUID.randomUUID().toString()
        MarketapWebBridge.sendCampaignToActiveWeb(targetCampaign, messageId)
    }

    private fun handleCampaign(
        targetCampaign: InAppCampaign,
        event: IngestEventRequest,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        logger.d { "Showing in-app campaign: ${targetCampaign.id} with layout type: ${targetCampaign.layout.layoutType}" }

        val resolvedCampaign = campaignFetchService.resolveCampaignHtml(targetCampaign, event)
            ?: return
        val campaignHtml = resolvedCampaign.html ?: return

        inAppView.show(
            campaignHtml,
            {
                campaignExposureService.recordImpression(resolvedCampaign.id)
                onImpression(resolvedCampaign)
                logger.d { "Recorded impression for campaign: ${resolvedCampaign.id}" }
            },
            { locationId ->
                onClick(resolvedCampaign, locationId)
                logger.d { "Recorded click for campaign: ${resolvedCampaign.id} at location: $locationId" }
                resolvedCampaign.id
            },
            { hideType ->
                logger.d { "Hiding campaign: ${resolvedCampaign.id} with hide type: $hideType" }
                when (hideType) {
                    HideType.HIDE_FOR_ONE_DAY -> campaignExposureService.hideCampaign(
                        resolvedCampaign.id, System.currentTimeMillis() + 1000 * 60 * 60 * 24
                    )

                    HideType.HIDE_FOR_SEVEN_DAYS -> campaignExposureService.hideCampaign(
                        resolvedCampaign.id, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7
                    )

                    HideType.HIDE_FOREVER -> campaignExposureService.hideCampaign(
                        resolvedCampaign.id,
                        System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10
                    )

                    HideType.CLOSE -> {}
                }
            },
            { eventName, properties ->
                onTrack(targetCampaign, eventName, properties)
            },
            onSetUserProperties,
        )
    }

    /**
     * 캠페인 숨김 처리 (웹브릿지에서 호출)
     */
    fun hideCampaign(campaignId: String, hideType: HideType) {
        logger.d { "Hiding campaign from web bridge: $campaignId with hide type: $hideType" }
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
