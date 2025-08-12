package com.marketap.sdk.model.internal

import android.os.Build
import java.util.Locale

internal data class Device(
    val gaid: String? = null,
    val appSetId: String? = null,
    val appLocalId: String? = null,
    val os: String? = "Android ${Build.VERSION.RELEASE}",
    val osVersion: String? = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
    val libraryVersion: String? = "1.1.5",
    val model: String? = Build.MODEL,
    val manufacturer: String? = Build.MANUFACTURER,
    val brand: String? = Build.BRAND,
    val token: String? = null,
    val appVersion: String? = "1.0.0",
    val appBuildNumber: String? = "100",
    val browserName: String? = null,
    val browserVersion: String? = null,
    val userAgent: String? = null,
    val timezone: String? = null,
    val locale: String? = Locale.getDefault().toString(),
    val screen: Screen? = null,
    val cpuArch: String? = null,
    val memoryTotal: Int? = null,
    val storageTotal: Int? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val networkType: String? = null,
    val carrier: String? = null,
    val hasSim: Boolean? = null,
    val maxTouchPoints: Int? = null,
    val camera: Boolean? = null,
    val microphone: Boolean? = null,
    val location: Boolean? = null,
    val notifications: Boolean? = null,
)
