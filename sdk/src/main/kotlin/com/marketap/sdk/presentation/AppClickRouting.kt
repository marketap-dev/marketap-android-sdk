package com.marketap.sdk.presentation

import android.content.Context
import com.marketap.sdk.model.internal.MarketapServerConfig
import com.marketap.sdk.model.internal.inapp.InAppOrigin

internal enum class AppClickRoute { WEB_VIEW, APP }

internal object AppClickRouting {
    /**
     * 커스텀 클릭 핸들러가 받지 않은 링크를 어디서 열지 정한다. 출처 웹뷰가 있는 메시지만 웹 클릭 처리 설정을 따른다.
     * @param originAvailable 출처 웹뷰가 붙어 있고 그 화면이 시작된 상태.
     */
    fun decide(
        hasOrigin: Boolean,
        hasCustomClickHandler: Boolean,
        useWebClickRouting: Boolean,
        originAvailable: Boolean,
    ): AppClickRoute = when {
        !hasOrigin || hasCustomClickHandler || !useWebClickRouting -> AppClickRoute.APP
        originAvailable -> AppClickRoute.WEB_VIEW
        else -> AppClickRoute.APP
    }

    /** 메인 스레드에서 부른다. */
    fun route(origin: InAppOrigin?): AppClickRoute = decide(
        hasOrigin = origin != null,
        hasCustomClickHandler = CustomHandlerStore.isCustomized(),
        useWebClickRouting = MarketapServerConfig.useWebClickRouting,
        originAvailable = origin?.canOpenUrl() == true,
    )

    /** 커스텀 클릭 핸들러 처리 뒤 메인 스레드에서 부른다. */
    fun open(context: Context, url: String, origin: InAppOrigin?) {
        if (origin != null && route(origin) == AppClickRoute.WEB_VIEW) {
            origin.openUrl(url)
        } else {
            InAppClickUrlHandler.open(context, url)
        }
    }
}
