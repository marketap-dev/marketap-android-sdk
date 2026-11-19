package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin

interface InAppView {
    fun isShowing(slot: InAppDisplaySlot): Boolean

    /**
     * @param origin 메시지를 띄운 이벤트가 들어온 웹뷰. 앱 화면 이벤트면 null.
     * @param closeOnPageLeave 출처 웹뷰가 페이지를 벗어나거나 화면에서 떨어지면 닫는다. 앱이 화면 전환 신호를 보내면 출처와 상관없이 닫는다.
     * @param timerStartAt 화면에 붙일 때 한 번 불러 템플릿 타이머의 시작 시각(`window._marketap_native.timerStartAt`, epoch ms)으로 넘긴다.
     * @param canMount 실제로 화면에 처음 붙이기 직전에 부른다. false 면 붙이지 않고 노출 기록 없이 자리를 반납한다.
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
        timerStartAt: () -> Long = System::currentTimeMillis,
        canMount: () -> Boolean = { true },
    ): Boolean

    /** 앱이 화면 전환 신호를 보냈다. [shownBeforeMs] 보다 먼저 화면에 나타난 "이 페이지에서만" 플로팅과 팝업을 닫는다. 아직 나타나지 않은 메시지는 닫지 않는다. */
    fun closeForAppScreenChange(shownBeforeMs: Long) {}
}
