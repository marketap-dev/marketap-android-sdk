package com.marketap.sdk.service

import android.app.Application
import android.util.Log
import com.marketap.sdk.api.MarketapApiImpl
import com.marketap.sdk.api.RetryMarketapBackend
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.service.activity.MarketapActivityLifecycleCallbacks
import com.marketap.sdk.service.inapp.CampaignComponentHandlerImpl
import com.marketap.sdk.service.inapp.ConditionCheckerImpl
import com.marketap.sdk.service.ingestion.MarketapEventService
import com.marketap.sdk.service.ingestion.inapp.InAppEventService
import com.marketap.sdk.service.push.MarketapNotificationOpenHandler
import com.marketap.sdk.service.state.DeviceManager
import com.marketap.sdk.service.state.MarketapStateManager
import com.marketap.sdk.service.state.SharedPreferenceInternalStorage
import com.marketap.sdk.service.state.inapp.InAppCampaignStateManagerImpl

internal fun initializeCore(config: MarketapConfig, application: Application): MarketapCore {
    Log.d("MarketapSDK", "Initializing Marketap SDK")
    val storage = SharedPreferenceInternalStorage(application)
    val marketapApi = MarketapApiImpl()
    val marketapBackend = RetryMarketapBackend(storage, marketapApi)
    val notificationOpenHandler = MarketapNotificationOpenHandler(marketapBackend, application)


    // Register activity lifecycle callbacks
    MarketapActivityLifecycleCallbacks(
        notificationOpenHandler = notificationOpenHandler,
        application = application
    )

    val deviceManager = DeviceManager(storage, marketapBackend, application)
    val inAppStateManager = InAppCampaignStateManagerImpl(storage, marketapBackend)
    val eventService = MarketapEventService(marketapBackend)
    val campaignComponentHandler =
        CampaignComponentHandlerImpl(inAppStateManager, application)

    val stateManager = MarketapStateManager(
        storage = storage,
        config = config,
        inAppCampaignStateManager = inAppStateManager,
        deviceManager = deviceManager,
        application = application
    )

    val inAppEventService = InAppEventService(
        eventService = eventService,
        campaignComponentHandler = campaignComponentHandler,
        conditionChecker = ConditionCheckerImpl(),
        inAppCampaignStateManager = inAppStateManager
    )

    return AndroidMarketapCore(
        stateManager = stateManager,
        eventService = inAppEventService,
        marketapBackend = marketapBackend,
        campaignComponentHandler = campaignComponentHandler
    )
}