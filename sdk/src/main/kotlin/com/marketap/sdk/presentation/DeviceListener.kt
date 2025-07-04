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
                FirebaseApp.initializeApp(application)
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    deviceManager.setToken(task.result)
                    userIngestionService.pushDevice()
                    logger.i("FCM token fetched successfully", task.result)
                } else {
                    logger.e("Failed to fetch FCM token", exception = task.exception)
                }
            }
        } catch (e: Exception) {
            logger.e("MarketapSDK", "Failed to fetch FCM token: ${e.message}")
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
                    logger.i("GAID fetched successfully", id)
                }
            } catch (e: Exception) {
                logger.e("MarketapSDK", "Failed to fetch GAID: ${e.message}")
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
            logger.e("MarketapSDK", "Failed to fetch AppSet ID: ${it.message}")
        }.addOnCompleteListener {
            userIngestionService.pushDevice()
            if (deviceManager.setFirstOpen()) {
                core.track("mkt_first_visit", emptyMap())
            }
        }
    }
}