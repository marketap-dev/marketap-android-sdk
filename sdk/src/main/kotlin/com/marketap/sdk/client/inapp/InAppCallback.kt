package com.marketap.sdk.client.inapp

import com.marketap.sdk.model.internal.inapp.HideType

interface InAppCallback {
    fun onClick(locationId: String)
    fun onHide(hideType: HideType)
}