package com.marketap.sdk

import android.app.Application
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.external.EventProperty
import com.marketap.sdk.service.MarketapCore
import com.marketap.sdk.service.initializeCore


object Marketap {
    internal var marketapCore: MarketapCore? = null
    private var config: MarketapConfig? = null
    private var application: Application? = null

    /**
     * SDK를 초기화합니다. Android의 가장 상단 Application 클래스에서 onCreate 메서드 내부에서 호출해야 합니다.
     *
     * @param application Android Application 인스턴스
     * @param projectId 프로젝트 ID
     * @param debug 디버그 모드 활성화 여부 (`null`일 경우 기본값 사용)
     */
    @JvmOverloads
    @JvmStatic
    fun initialize(application: Application, projectId: String, debug: Boolean? = null) {
        val config = MarketapConfig(projectId, debug == true)
        if (marketapCore == null || this.config?.projectId != config.projectId || application !== this.application) {
            marketapCore = try {
                this.config = config
                this.application = application
                initializeCore(config, application)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 사용자를 로그인합니다.
     * 해당 함수에서는 로그인 이벤트와 함께 내부적으로 user Id를 설정합니다.
     * track + identify와 동일한 동작을 수행합니다.
     *
     * @param userId 사용자 ID
     * @param userProperties 사용자 속성 정보 (선택 사항)
     * @param eventProperties 이벤트 속성 정보 (선택 사항)
     */
    @JvmOverloads
    @JvmStatic
    fun login(
        userId: String,
        userProperties: Map<String, Any>? = null,
        eventProperties: Map<String, Any>? = null
    ) {
        marketapCore?.identify(userId, userProperties) {
            marketapCore?.track("mkt_login", eventProperties, null, null)
        }
    }

    /**
     * 사용자를 로그아웃합니다.
     * 해당 함수에서는 로그아웃 이벤트와 함께 내부적으로 user Id를 초기화 합니다.
     * track + resetIdentity와 동일한 동작을 수행합니다.
     *
     * @param properties 로그아웃 시 이벤트에 추가할 속성
     */
    @JvmOverloads
    @JvmStatic
    fun logout(properties: Map<String, Any>? = null) {
        marketapCore?.track("mkt_logout", properties, null, null) {
            marketapCore?.resetIdentity()
        }
    }

    /**
     * 특정 이벤트를 추적합니다.
     *
     * @param name 이벤트 이름
     * @param properties 이벤트 속성 정보 (선택 사항)
     */
    @JvmOverloads
    @JvmStatic
    fun track(
        name: String,
        properties: Map<String, Any>? = null
    ) {
        marketapCore?.track(name, properties, null, null)
    }


    /**
     * 구매 이벤트를 추적합니다.
     * track("mkt_purchase")와 동일한 동작을 수행합니다.
     *
     * @param revenue 구매 금액
     * @param properties 추가적인 구매 속성 (선택 사항)
     */
    @JvmOverloads
    @JvmStatic
    fun trackPurchase(revenue: Double, properties: Map<String, Any>? = null) {
        marketapCore?.track(
            "mkt_purchase",
            mapOf("mkt_revenue" to revenue) + (properties ?: emptyMap()),
            null,
            null
        )
    }

    /**
     * 특정 수익 이벤트를 추적합니다.
     *
     * @param name 이벤트 이름
     * @param revenue 수익 금액
     * @param properties 추가적인 이벤트 속성 (선택 사항)
     */
    @JvmOverloads
    @JvmStatic
    fun trackRevenue(name: String, revenue: Double, properties: Map<String, Any>? = null) {
        marketapCore?.track(
            name,
            properties?.plus(EventProperty.Builder().setRevenue(revenue).build()),
            null,
            null
        )
    }

    /**
     * 페이지 뷰를 추적합니다.
     * track("mkt_page_view")와 동일한 동작을 수행합니다.
     *
     * @param properties 추가적인 속성 정보 (선택 사항)
     */
    @JvmOverloads
    @JvmStatic
    fun trackPageView(properties: Map<String, Any>? = null) {
        marketapCore?.track("mkt_page_view", properties, null, null)
    }


    /**
     * 사용자 정보를 업데이트합니다.
     * 해당 함수에서는 사용자 정보 업데이트 요청과 함께 내부적으로 user Id를 설정합니다.
     *
     * @param userId 사용자 ID
     * @param properties 사용자 속성 정보 (선택 사항)
     */
    @JvmOverloads
    @JvmStatic
    fun identify(userId: String, properties: Map<String, Any>? = null) {
        marketapCore?.identify(userId, properties)
    }


    /**
     * 사용자 ID 및 속성을 초기화합니다.
     * SDK에 저장된 사용자 ID 및 속성 정보를 초기화합니다.
     *
     */
    @JvmStatic
    fun resetIdentity() {
        marketapCore?.resetIdentity()
    }
}