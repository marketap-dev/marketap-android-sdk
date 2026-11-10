package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot

internal object InAppPageScope {
    /** 화면 이동 값 `PAGE`("이 페이지에서만"). 그 외 값과 값 없음은 "닫을 때까지 유지"다. */
    const val FLOATING_SCOPE_PAGE = "PAGE"

    const val PAGE_VIEW_EVENT = "mkt_page_view"

    /** 출처 웹뷰가 페이지를 벗어나면 닫는 메시지. 출처 없는 "이 페이지에서만"은 닫을 때까지 유지한다. */
    fun closesOnPageLeave(slot: InAppDisplaySlot, floatingScope: String?, hasOrigin: Boolean): Boolean =
        hasOrigin && slot == InAppDisplaySlot.FLOATING && floatingScope.equals(FLOATING_SCOPE_PAGE, ignoreCase = true)

    /** `addressChanged` 를 한 번도 보내지 않은 웹(구버전 웹 SDK)은 페이지뷰 이벤트를 주소 변경으로 본다. */
    fun pageViewLeavesPage(eventName: String, addressChangeReceived: Boolean): Boolean =
        !addressChangeReceived && eventName == PAGE_VIEW_EVENT
}
