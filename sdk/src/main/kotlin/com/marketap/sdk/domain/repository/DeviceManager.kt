package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.Device

internal interface DeviceManager {
    fun isDeviceReady(): Boolean
    fun setFirstOpen(): Boolean
    fun getDevice(): Device
    fun setToken(token: String)
    fun setAppSetId(appSetId: String)
    fun setGoogleAdvertisingId(gaid: String)
}