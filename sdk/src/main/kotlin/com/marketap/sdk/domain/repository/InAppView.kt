package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin

interface InAppView {
    fun isShowing(slot: InAppDisplaySlot): Boolean

    /**
     * @param origin 메시지를 띄운 이벤트가 들어온 웹뷰. 앱 화면 이벤트면 null.
     * @param closeOnPageLeave 출처 웹뷰가 페이지를 벗어나거나 화면에서 떨어지면 닫는다.
     * @return 그 자리를 차지해 표시를 시작했으면 true, 이미 차 있으면 false.
     */
    fun show(
        html: String,
        slot: InAppDisplaySlot,
        origin: InAppOrigin?,
        closeOnPageLeave: Boolean,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ): Boolean
}
