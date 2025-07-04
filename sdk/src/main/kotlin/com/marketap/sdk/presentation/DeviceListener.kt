package com.marketap.sdk.presentation

import android.app.Application
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.utils.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class DeviceListener(
    private val deviceManager: DeviceManager,
    private val userIngestionService: UserIngestionService,
    private val application: Application,
    private val core: MarketapCoreService,
) {
    fun init() {
        addTokenListener()
        addGAIDListener()
    }

    private fun addTokenListener() {
        try {
            if (FirebaseApp.getApps(application).isEmpty()) {
                logger.d { "FirebaseApp not initialized, initializing now" }
                FirebaseApp.initializeApp(application)
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    deviceManager.setToken(task.result)
                    userIngestionService.pushDevice()
                    logger.d { "FCM token fetched successfully" }
                    logger.d { "This device's push token is [${task.result}]. Please use this token to test push message in Marketap console" }
                } else {
                    logger.e(task.exception) { "Failed to fetch FCM token" }
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch FCM token" }
        }
    }

    private fun addGAIDListener() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(application)
                val id = adInfo.id
                if (id != null && id != "00000000-0000-0000-0000-000000000000") {
                    deviceManager.setGoogleAdvertisingId(id)
                    userIngestionService.pushDevice()
                    logger.d { "GAID fetched successfully with id: [$id]" }
                    logger.d { "Your Marketap Device ID is [gaid:$id]" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to fetch GAID" }
            } finally {
                addAppSetIdListener()
            }
        }
    }

    private fun addAppSetIdListener() {
        val client: AppSetIdClient = AppSet.getClient(application)
        client.appSetIdInfo.addOnSuccessListener { appSetIdInfo ->
            val appSetId = appSetIdInfo.id
            deviceManager.setAppSetId(appSetId)
        }.addOnFailureListener {
            logger.e(it) { "Failed to fetch AppSet ID" }
        }.addOnCompleteListener {
            userIngestionService.pushDevice()
            if (deviceManager.setFirstOpen()) {
                core.track("mkt_first_visit", emptyMap())
            }
        }
    }
}