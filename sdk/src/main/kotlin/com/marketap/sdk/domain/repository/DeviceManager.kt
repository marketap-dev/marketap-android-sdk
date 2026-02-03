package com.marketap.sdk.domain.repository

import android.app.Activity
import android.content.Context
import com.marketap.sdk.model.internal.Device

internal interface DeviceManager {
    fun isDeviceReady(): Boolean
    fun setFirstOpen(): Boolean
    fun getDevice(): Device
    fun setToken(token: String)
    fun setDeviceOptIn(optIn: Boolean)
    fun setAppSetId(appSetId: String)
    fun setGoogleAdvertisingId(gaid: String)
    fun requestAuthorizationForPushNotifications(activity: Activity)
    fun isPushNotificationEnabled(context: Context): Boolean
}