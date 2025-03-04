package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.HideType

internal class InAppService(
    private val campaignExposureService: CampaignExposureService,
    private val eventConditionChecker: ConditionChecker,
    private val campaignFetchService: CampaignFetchService,
    private val inAppView: InAppView
) {

    fun onEvent(
        event: IngestEventRequest,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit
    ) {
        campaignFetchService.useCampaigns { campaigns ->
            val targetCampaign = campaigns.find { campaign ->
                if (!eventConditionChecker.checkCondition(
                        campaign.triggerEventCondition.condition,
                        event.name,
                        event.properties
                    )
                ) {
                    return@find false
                }
                if (campaignExposureService.isCampaignHidden(campaign.id)) {
                    return@find false
                }

                campaign.triggerEventCondition.frequencyCap?.let { frequencyCap ->
                    if (campaignExposureService.hasReachedImpressionLimit(
                            campaign.id,
                            frequencyCap.durationMinutes,
                            frequencyCap.limit
                        )
                    ) {
                        return@find false
                    }
                }
                true
            }

            targetCampaign?.let {
                handleCampaign(it, onImpression, onClick)
            }
        }
    }

    private fun handleCampaign(
        targetCampaign: InAppCampaign,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit
    ) {
        inAppView.show(
            targetCampaign.html,
            {
                campaignExposureService.recordImpression(targetCampaign.id); onImpression(
                targetCampaign
            )
            },
            { locationId ->
                onClick(targetCampaign, locationId)
            },
            { hideType ->
                when (hideType) {
                    HideType.HIDE_FOR_ONE_DAY -> campaignExposureService.hideCampaign(
                        targetCampaign.id, System.currentTimeMillis() + 1000 * 60 * 60 * 24
                    )

                    HideType.HIDE_FOR_SEVEN_DAYS -> campaignExposureService.hideCampaign(
                        targetCampaign.id, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7
                    )

                    HideType.HIDE_FOREVER -> campaignExposureService.hideCampaign(
                        targetCampaign.id,
                        System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10
                    )

                    HideType.CLOSE -> {}
                }
            })
    }
}