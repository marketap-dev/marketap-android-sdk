package com.marketap.sdk.presentation

import android.app.Application
import com.marketap.sdk.client.AndroidDeviceManager
import com.marketap.sdk.client.AndroidSessionManager
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.client.inapp.AndroidInAppView
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.CampaignExposureService
import com.marketap.sdk.domain.service.inapp.CampaignFetchService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.condition.ConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.MarketapConfig

internal fun initializeCore(
    config: MarketapConfig,
    application: Application
): MarketapCoreService {
    val storage = SharedPreferenceInternalStorage(application)
    val marketapApi = MarketapApiImpl(debug = config.debug)
    val marketapBackend = RetryMarketapBackend(storage, marketapApi)
    val clientStateManager = ClientStateManager(config, storage)
    val deviceManager = AndroidDeviceManager(storage, application)
    val sessionManager = AndroidSessionManager(storage)

    val notificationOpenHandler = MarketapNotificationOpenHandler(marketapBackend, application)
    val activityManager = ActivityManager()
    // Register activity lifecycle callbacks
    MarketapActivityLifecycleCallbacks(
        notificationOpenHandler = notificationOpenHandler,
        application = application,
        activityManager = activityManager
    )


    val inAppService = InAppService(
        CampaignExposureService(storage),
        ConditionCheckerImpl(PropertyConditionCheckerImpl(ValueComparatorImpl())),
        CampaignFetchService(storage, marketapBackend, clientStateManager, deviceManager),
        AndroidInAppView.getInstance().apply { init(application) }
    )

    val eventIngestionService = EventIngestionService(
        marketapBackend,
        inAppService,
        clientStateManager,
        sessionManager,
        deviceManager
    )

    val userIngestionService = UserIngestionService(
        clientStateManager,
        deviceManager,
        marketapBackend,
    )

    val core = MarketapCoreService(
        eventIngestionService,
        userIngestionService,
    )

    val deviceListener = DeviceListener(deviceManager, userIngestionService, application, core)
    deviceListener.init()
    return core
}