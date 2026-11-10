package com.marketap.sdk.model.internal.inapp

/** 인앱 메시지가 뜨는 자리. 자리마다 한 번에 하나씩 뜨고, 서로 다른 자리는 동시에 뜰 수 있다. */
enum class InAppDisplaySlot {
    POPUP,
    FLOATING;

    companion object {
        /** 서버 `displaySlot` 값. FLOATING 일 때만 오고, 없거나 모르는 값이면 [POPUP]. */
        fun of(value: String?): InAppDisplaySlot =
            if (value.equals(FLOATING.name, ignoreCase = true)) FLOATING else POPUP
    }
}
