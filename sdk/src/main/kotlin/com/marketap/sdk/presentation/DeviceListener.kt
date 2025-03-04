package com.marketap.sdk.presentation

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.service.event.UserIngestionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class DeviceListener(
    private val deviceManager: DeviceManager,
    private val userIngestionService: UserIngestionService,
    private val application: Application,
) {
    fun init() {
        userIngestionService.pushDevice()
        addTokenListener()
        addGAIDListener()
        addAppSetIdListener()
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
                }
            }
        } catch (e: Exception) {
            Log.e("MarketapSDK", "Failed to fetch FCM token: ${e.message}")
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
                }
            } catch (e: Exception) {
                Log.e("MarketapSDK", "Failed to fetch GAID: ${e.message}")
            }
        }
    }

    private fun addAppSetIdListener() {
        val client: AppSetIdClient = AppSet.getClient(application)
        client.appSetIdInfo.addOnSuccessListener { appSetIdInfo ->
            val appSetId = appSetIdInfo.id
            deviceManager.setAppSetId(appSetId)
        }.addOnFailureListener {
            Log.e("MarketapSDK", "Failed to fetch AppSet ID: ${it.message}")
        }
    }
}