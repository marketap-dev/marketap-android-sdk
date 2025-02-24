package com.marketap.sdk.service.inapp

import android.net.Uri
import com.marketap.sdk.model.internal.inapp.HideType

interface InAppCallBack {
    fun hideCampaign(campaignId: String, hideType: HideType)
    fun click(campaignId: String, locationId: String, uri: Uri)
}