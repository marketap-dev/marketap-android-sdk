package com.marketap.sdk.client.inapp

import com.marketap.sdk.model.internal.inapp.HideType

interface InAppCallback {
    fun onClick(locationId: String): String?
    fun onHide(hideType: HideType)
    fun onTrack(eventName: String, eventPropertiesJson: String)
    fun onSetUserProperties(userPropertiesJson: String)
}