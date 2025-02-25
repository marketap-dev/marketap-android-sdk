package com.marketap.sdk.service.state

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.view.ViewConfiguration
import com.marketap.sdk.api.MarketapBackend
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.Screen
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.utils.getTypeToken
import java.util.Locale
import java.util.UUID

internal class DeviceManager(
    private val storage: InternalStorage,
    private val marketapBackend: MarketapBackend,
    context: Context
) {
    init {
        setMaxTouchPoints(context)
    }
    
    private var token: String? = null
    fun setToken(token: String) {
        this.token = token
    }

    fun setAppSetId(appSetId: String, projectId: String) {
        val device = getDevice()
        if (device.appSetId == appSetId) {
            return
        } else {
            storage.setItem("app_set_id", appSetId)
            updateDevice(projectId)
        }
    }

    fun setGoogleAdvertisingId(gaid: String, projectId: String) {
        val device = getDevice()
        if (device.gaid == gaid) {
            return
        } else {
            storage.setItem("gaid", gaid)
            updateDevice(projectId)
        }
    }

    fun updateDevice(projectId: String, removeUserId: Boolean? = false) {
        val device = getDevice()
        marketapBackend.updateDevice(projectId, device.toReq(removeUserId))
    }


    private fun getOrCreateLocalId(): String {
        val savedId = storage.getItem("_marketap_local_id", getTypeToken<String>())
        return if (savedId != null) {
            savedId
        } else {
            val newId = UUID.randomUUID().toString()
            storage.setItem("_marketap_local_id", newId)
            newId
        }
    }


    fun getDevice(): Device {

        val displayMetrics = Resources.getSystem().displayMetrics
        val screen = Screen(
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            pixelRatio = displayMetrics.density
        )

        return Device(
            gaid = storage.getItem("gaid", getTypeToken<String>()),
            appSetId = storage.getItem("app_set_id", getTypeToken<String>()),
            appLocalId = getOrCreateLocalId(),
            token = token,
            os = "Android ${Build.VERSION.RELEASE}",
            osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            libraryVersion = "1.0.0",
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            appVersion = "1.0.0",
            appBuildNumber = "100",
            locale = Locale.getDefault().toString(),
            screen = screen,
            maxTouchPoints = maxTouchPoints
        )
    }

    private var maxTouchPoints: Int = 1
    private fun setMaxTouchPoints(context: Context) {
        val packageManager = context.packageManager
        packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        val config = ViewConfiguration.get(context)
        maxTouchPoints = (config.scaledMaximumFlingVelocity / 1000).coerceAtLeast(1)
    }
}