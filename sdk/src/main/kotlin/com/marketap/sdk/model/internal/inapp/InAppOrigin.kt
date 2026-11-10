package com.marketap.sdk.model.internal.inapp

/** 인앱 메시지를 띄운 이벤트가 들어온 웹뷰(인앱 처리를 맡은 웹브릿지). 앱 화면 이벤트로 뜬 메시지에는 없다. */
interface InAppOrigin {
    /** 웹뷰가 붙어 있고 그 화면이 시작된 상태라 링크를 그 웹뷰에서 열 수 있으면 true. 메인 스레드에서 부른다. */
    fun canOpenUrl(): Boolean

    /** 메인 스레드에서 부른다. */
    fun openUrl(url: String)

    /** 웹뷰가 화면에서 떨어졌거나 래퍼 브릿지가 해제됐으면 true. 그 뒤에 뜨는 메시지는 출처 없이 앱 화면 메시지처럼 띄운다. */
    val isGone: Boolean get() = false
}
