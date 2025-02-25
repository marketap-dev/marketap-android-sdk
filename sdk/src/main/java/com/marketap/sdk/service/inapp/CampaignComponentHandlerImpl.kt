package com.marketap.sdk.service.inapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.service.inapp.resource.InAppMessageActivity
import com.marketap.sdk.service.state.inapp.InAppCampaignStateManagerImpl

internal class CampaignComponentHandlerImpl(
    private val inAppStateManager: InAppCampaignStateManagerImpl,
    private val context: Context
) : CampaignComponentHandler {
    private val onClicks = mutableMapOf<String, (String) -> Unit>()

    override fun showCampaign(
        campaign: InAppCampaign,
        onImpression: () -> Unit,
        onClick: (locationId: String) -> Unit
    ) {
        onImpression()
        onClicks[campaign.id] = onClick
        val intent = Intent(context, InAppMessageActivity::class.java).apply {
            putExtra("htmlData", campaign.html)
            putExtra("campaignId", campaign.id)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }


    override fun hideCampaign(campaignId: String, hideType: HideType) {
        val hideUntil = when (hideType) {
            HideType.HIDE_FOREVER -> Long.MAX_VALUE
            HideType.HIDE_FOR_ONE_DAY -> System.currentTimeMillis() + 24 * 60 * 60 * 1000
            HideType.HIDE_FOR_SEVEN_DAYS -> System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000
            HideType.CLOSE -> return
        }

        inAppStateManager.hideCampaign(campaignId, hideUntil)
    }


    override fun click(campaignId: String, locationId: String, uri: Uri) {
        onClicks[campaignId]?.invoke(locationId)
        onClicks.remove(campaignId)
    }
}