package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot

internal object InAppPageScope {
    /** 화면 이동 값 `PAGE`("이 페이지에서만"). 그 외 값과 값 없음은 "닫을 때까지 유지"다. */
    const val FLOATING_SCOPE_PAGE = "PAGE"

    const val PAGE_VIEW_EVENT = "mkt_page_view"

    /** 페이지를 벗어나면 닫는 메시지("이 페이지에서만" 플로팅). 앱의 화면 전환 신호에 닫고, 웹뷰에서 시작됐으면 그 웹뷰가 페이지를 벗어날 때도 닫는다. */
    fun closesOnPageLeave(slot: InAppDisplaySlot, floatingScope: String?): Boolean =
        slot == InAppDisplaySlot.FLOATING && floatingScope.equals(FLOATING_SCOPE_PAGE, ignoreCase = true)

    /** 웹뷰에서 온 페이지뷰 이벤트는 페이지 이동이다. 쿼리만 바뀐 페이지도 페이지뷰를 보내면 이동으로 본다. */
    fun pageViewLeavesPage(eventName: String): Boolean = eventName == PAGE_VIEW_EVENT
}
