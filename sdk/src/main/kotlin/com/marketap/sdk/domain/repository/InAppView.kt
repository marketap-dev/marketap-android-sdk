package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.inapp.HideType

interface InAppView {
    fun show(
        html: String,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    )
}