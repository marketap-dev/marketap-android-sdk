package com.marketap.sdk

import android.app.Activity
import android.app.Application
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.external.EventProperty
import com.marketap.sdk.model.external.MarketapClickHandler
import com.marketap.sdk.model.external.MarketapLogLevel
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.presentation.Dependency.initializeCore
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.presentation.MarketapRegistry.marketapCore
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.mapAdapter
import com.marketap.sdk.utils.serialize


object Marketap {
    /**
     * SDK를 초기화합니다. Android의 가장 상단 Application 클래스에서 onCreate 메서드 내부에서 호출해야 합니다.
     *
     * @param application Android Application 인스턴스
     * @param projectId 프로젝트 ID
     */
    @JvmStatic
    fun initialize(application: Application, projectId: String) {
        logger.v { "Marketap SDK start initializing with projectId $projectId" }
        val config = MarketapConfig(projectId)
        if (!MarketapRegistry.isInitialized || MarketapRegistry.config?.projectId != config.projectId || application !== MarketapRegistry.application) {
            marketapCore = try {
                MarketapRegistry.config = config
                MarketapRegistry.application = application
                initializeCore(config, application).also {
                    MarketapRegistry.isInitialized = true
                }
            } catch (e: Exception) {
                logger.e(e) { "Marketap SDK initialization failed with projectId  ${config.projectId}" }
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
        logger.d {
            "Marketap SDK login with " +
                    "userId: $userId, " +
                    "userProperties: ${userProperties?.serialize(mapAdapter<String, Any>())}, " +
                    "eventProperties: ${eventProperties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.identify(userId, userProperties)
        marketapCore?.track("mkt_login", eventProperties)
    }

    /**
     * 사용자를 로그인합니다.
     * 해당 함수에서는 로그인 이벤트와 함께 내부적으로 user Id를 설정합니다.
     * track + identify와 동일한 동작을 수행합니다.
     *
     * @param userId 사용자 ID
     * @param userProperties 사용자 속성 정보 (선택 사항)
     * @param eventProperties 이벤트 속성 정보 (선택 사항)
     * @param persistUser 사용자의 로그인 상태를 지속적으로 유지할지 여부 (기본값: true)
     */
    @JvmOverloads
    @JvmStatic
    fun signup(
        userId: String,
        userProperties: Map<String, Any>? = null,
        eventProperties: Map<String, Any>? = null,
        persistUser: Boolean = true
    ) {
        logger.d {
            "Marketap SDK signup with " +
                    "userId: $userId, " +
                    "userProperties: ${userProperties?.serialize(mapAdapter<String, Any>())}, " +
                    "eventProperties: ${eventProperties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.identify(userId, userProperties)
        marketapCore?.track("mkt_signup", eventProperties)
        if (!persistUser) {
            logger.d { "Marketap SDK signup with persistUser = false, resetting identity" }
            marketapCore?.resetIdentity()
        } else {
            logger.d { "Marketap SDK signup with persistUser = true, identity will be retained" }
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
        logger.d {
            "Marketap SDK logout with properties: ${properties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.track("mkt_logout", properties)
        marketapCore?.resetIdentity()

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
        logger.d {
            "Marketap SDK track event with name: $name, " +
                    "properties: ${properties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.track(name, properties)
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
        logger.d {
            "Marketap SDK track purchase" +
                    "revenue: $revenue, properties: ${properties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.track(
            "mkt_purchase",
            mapOf("mkt_revenue" to revenue) + (properties ?: emptyMap()),
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
        logger.d {
            "Marketap SDK track revenue event with name: $name, " +
                    "revenue: $revenue, properties: ${properties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.track(
            name,
            properties?.plus(EventProperty.Builder().setRevenue(revenue).build()),
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
        logger.d {
            "Marketap SDK track page view with properties: ${properties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.track("mkt_page_view", properties)
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
        logger.d {
            "Marketap SDK identify user with userId: $userId, " +
                    "properties: ${properties?.serialize(mapAdapter<String, Any>())}"
        }
        marketapCore?.identify(userId, properties)
    }


    /**
     * 사용자 ID 및 속성을 초기화합니다.
     * SDK에 저장된 사용자 ID 및 속성 정보를 초기화합니다.
     *
     */
    @JvmStatic
    fun resetIdentity() {
        logger.d {
            "Marketap SDK reset identity"
        }
        marketapCore?.resetIdentity()
    }


    @JvmStatic
    fun setClickHandler(clickHandler: MarketapClickHandler) {
        logger.i { "setClickHandler: ${clickHandler::class.java.name}" }
        CustomHandlerStore.setClickHandler(clickHandler)
    }

    @JvmStatic
    fun setLogLevel(logLevel: MarketapLogLevel) {
        logger.i {
            "setLogLevel: ${logLevel.name}"
        }
        MarketapRegistry.logLevel = logLevel
    }

    @JvmStatic
    fun requestAuthorizationForPushNotifications(activity: Activity) {
        logger.i { "requestAuthorizationForPushNotifications on ${activity::class.java.name}" }
        marketapCore?.requestAuthorizationForPushNotifications(activity)
    }
}

/**
 * 인앱 이벤트 속성 빌더
 */
internal object InAppEventBuilder {

    @JvmOverloads
    fun impressionEventProperties(
        campaignId: String,
        messageId: String,
        layoutSubType: String?,
        sessionId: String? = null
    ): Map<String, Any> {
        val props = mutableMapOf<String, Any>(
            "mkt_campaign_id" to campaignId,
            "mkt_campaign_category" to "ON_SITE",
            "mkt_channel_type" to "IN_APP_MESSAGE",
            "mkt_sub_channel_type" to (layoutSubType ?: "MODAL"),
            "mkt_result_status" to 200000,
            "mkt_result_message" to "SUCCESS",
            "mkt_is_success" to true,
            "mkt_message_id" to messageId
        )
        if (sessionId != null) {
            props["mkt_session_id"] = sessionId
        }
        return props
    }

    @JvmOverloads
    fun clickEventProperties(
        campaignId: String,
        messageId: String,
        locationId: String,
        url: String?,
        layoutSubType: String?,
        sessionId: String? = null
    ): Map<String, Any> {
        val props = mutableMapOf<String, Any>(
            "mkt_campaign_id" to campaignId,
            "mkt_campaign_category" to "ON_SITE",
            "mkt_channel_type" to "IN_APP_MESSAGE",
            "mkt_sub_channel_type" to (layoutSubType ?: "MODAL"),
            "mkt_result_status" to 200000,
            "mkt_result_message" to "SUCCESS",
            "mkt_is_success" to true,
            "mkt_message_id" to messageId,
            "mkt_location_id" to locationId
        )
        if (url != null) {
            props["mkt_url"] = url
        }
        if (sessionId != null) {
            props["mkt_session_id"] = sessionId
        }
        return props
    }
}