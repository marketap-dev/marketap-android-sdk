package com.marketap.sdk.service.ingestion.inapp

import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.service.inapp.CampaignComponentHandler
import com.marketap.sdk.service.inapp.ConditionChecker
import com.marketap.sdk.service.ingestion.EventService
import com.marketap.sdk.service.state.inapp.InAppCampaignStateManager

internal class InAppEventService(
    private val eventService: EventService,
    private val campaignComponentHandler: CampaignComponentHandler,
    private val conditionChecker: ConditionChecker,
    private val inAppCampaignStateManager: InAppCampaignStateManager
) : EventService {
    override fun ingestEvent(projectId: String, eventRequest: IngestEventRequest) {
        eventService.ingestEvent(projectId, eventRequest)
        inAppCampaignStateManager.getInAppCampaigns(
            FetchCampaignReq(
                projectId = projectId,
                userId = eventRequest.userId,
                device = eventRequest.device,
            )
        ) { campaigns ->
            val targetCampaign = campaigns.find {
                !isCampaignHidden(
                    it,
                    inAppCampaignStateManager
                ) && conditionChecker.checkCondition(
                    it.triggerEventCondition.condition,
                    eventRequest.name,
                    eventRequest.properties
                )
            } ?: return@getInAppCampaigns

            val onImpression = {
                eventService.ingestEvent(
                    projectId, IngestEventRequest.delivery(
                        userId = eventRequest.userId,
                        device = eventRequest.device,
                        properties = AppEventProperty.onSite(targetCampaign),
                    )
                )
            }

            val onClick = { locationId: String ->
                eventService.ingestEvent(
                    projectId,
                    IngestEventRequest.click(
                        userId = eventRequest.userId,
                        device = eventRequest.device,
                        properties = AppEventProperty.onSite(targetCampaign)
                            .addLocationId(locationId),
                    )
                )
            }

            campaignComponentHandler.showCampaign(
                targetCampaign, onImpression, onClick,
            )
        }
    }

    private fun isCampaignHidden(
        campaign: InAppCampaign,
        inAppCampaignStateManager: InAppCampaignStateManager
    ): Boolean {
        if (inAppCampaignStateManager.isCampaignHidden(campaign.id)) {
            return true
        }

        val frequencyCap = campaign.triggerEventCondition.frequencyCap
        if (frequencyCap != null) {
            val targetTime =
                System.currentTimeMillis() - frequencyCap.durationMinutes * 60 * 1000
            val impressionCount =
                inAppCampaignStateManager.getImpressionCount(campaign.id, targetTime)
            if (impressionCount >= frequencyCap.limit) {
                return true
            }
        }

        return false
    }
}