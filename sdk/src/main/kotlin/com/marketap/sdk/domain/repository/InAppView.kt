package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot

interface InAppView {
    fun isShowing(slot: InAppDisplaySlot): Boolean

    /** @return 그 자리를 차지해 표시를 시작했으면 true, 이미 차 있으면 false. */
    fun show(
        html: String,
        slot: InAppDisplaySlot,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ): Boolean
}
