package com.marketap.sdk.client.inapp.popup

import android.os.Build

internal enum class PopupBackMechanism {
    /** `ComponentActivity` 의 `OnBackPressedDispatcher` 에 콜백을 건다. */
    ON_BACK_PRESSED,

    /** API 33+ 에서 `OnBackInvokedDispatcher` 에 `PRIORITY_OVERLAY` 로 건다. 앱이 `enableOnBackInvokedCallback` 을 켜지 않았으면 플랫폼이 무시한다. */
    ON_BACK_INVOKED,

    /** 팝업 컨테이너가 포커스를 잡고 `KEYCODE_BACK` 을 받는다. 닫을 때 원래 포커스로 되돌린다. */
    KEY_EVENT,
}

/** 붙을 화면에 걸 뒤로가기 수단. 둘 이상이면 모두 걸고, 먼저 온 신호 하나만 처리된다. */
internal fun popupBackMechanisms(isComponentActivity: Boolean, sdkInt: Int): Set<PopupBackMechanism> = when {
    isComponentActivity -> setOf(PopupBackMechanism.ON_BACK_PRESSED)
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> setOf(PopupBackMechanism.ON_BACK_INVOKED, PopupBackMechanism.KEY_EVENT)
    else -> setOf(PopupBackMechanism.KEY_EVENT)
}
