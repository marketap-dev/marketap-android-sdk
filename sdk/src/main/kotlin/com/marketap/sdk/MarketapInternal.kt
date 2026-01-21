package com.marketap.sdk

/**
 * MarketapInternal - Flutter/React Native 플러그인에서 사용하는 내부 API
 * 일반 고객사에서는 사용하지 않습니다.
 */
object MarketapInternal {

    // MARK: - 외부 웹브릿지 인앱 이벤트 처리 (Flutter/React Native)

    /**
     * 외부 웹브릿지에서 인앱 메시지 노출 이벤트 처리
     */
    @JvmStatic
    fun handleExternalInAppImpression(
        campaignId: String,
        messageId: String,
        layoutSubType: String?
    ) {
        Marketap.handleInAppImpression(campaignId, messageId, layoutSubType)
    }

    /**
     * 외부 웹브릿지에서 인앱 메시지 클릭 이벤트 처리
     * 클릭 핸들러 호출 + 이벤트 트래킹
     */
    @JvmStatic
    fun handleExternalInAppClick(
        campaignId: String,
        messageId: String,
        locationId: String,
        url: String?,
        layoutSubType: String?
    ) {
        Marketap.handleInAppClick(campaignId, messageId, locationId, url, layoutSubType)
    }

    /**
     * 외부 웹브릿지에서 인앱 메시지 숨김 처리
     */
    @JvmStatic
    fun handleExternalInAppHide(campaignId: String, hideType: String?) {
        Marketap.handleInAppHide(campaignId, hideType)
    }

    // MARK: - 웹브릿지 이벤트 처리

    /**
     * 웹브릿지에서 호출된 이벤트를 추적합니다.
     * 인앱 캠페인이 웹으로 위임되어 처리됩니다.
     */
    @JvmOverloads
    @JvmStatic
    fun trackFromWebBridge(name: String, properties: Map<String, Any>? = null) {
        Marketap.trackFromWebBridge(name, properties)
    }

    /**
     * 유저 속성을 업데이트합니다.
     */
    @JvmStatic
    fun setUserProperties(properties: Map<String, Any>) {
        Marketap.setUserProperties(properties)
    }
}
