package com.marketap.sdk.client.inapp.popup

/** 자리를 쥐고 있는 팝업 하나. 표시를 시작하기 전에 먼저 기록해 그사이 온 닫기 요청이 대상을 찾게 한다. */
internal class PopupHolder<T : Any> {
    @Volatile
    var current: T? = null
        private set

    fun publishThenShow(popup: T, show: (T) -> Unit) {
        current = popup
        show(popup)
    }

    /** [popup] 이 닫혔다. 아직 기록돼 있는 팝업일 때만 지운다. */
    fun clear(popup: T) {
        if (current === popup) current = null
    }
}
