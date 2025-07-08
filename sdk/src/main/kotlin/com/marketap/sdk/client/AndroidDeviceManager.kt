package com.marketap.sdk.client

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.Screen
import com.marketap.sdk.utils.booleanAdapter
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.stringAdapter
import java.util.UUID

internal class AndroidDeviceManager(
    private val storage: InternalStorage,
) : DeviceManager {

    private var token: String? = null


    override fun setToken(token: String) {
        this.token = token
    }

    override fun setAppSetId(appSetId: String) {
        storage.setItem("app_set_id", appSetId, stringAdapter)
    }

    override fun setGoogleAdvertisingId(gaid: String) {
        storage.setItem("gaid", gaid, stringAdapter)
    }

    private val REQ_POST_NOTI = 0xA7
    override fun requestAuthorizationForPushNotifications(activity: Activity) {
        /* ───────────── Android 13+ (API 33) ───────────── */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission == PackageManager.PERMISSION_GRANTED) {
                logger.d { "Notification permission already granted" }
                logger.d { "Token: $token" }
                return
            }


            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                logger.d { "Permission permanently denied." }
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTI
                )
                logger.d { "Requesting notification permission" }
            }
            return
        }

        /* ───────────── Android 12 이하 ───────────── */
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {          // API 26+
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    }
                } else {                                                       // API 25 이하
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            logger.d { "Requesting notification settings" }
            activity.startActivity(intent)
        } else {
            logger.d { "Notification permission already granted" }
        }
    }

    override fun isPushNotificationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission == PackageManager.PERMISSION_GRANTED) {
                logger.d { "Notification permission already granted $token" }
                return true
            } else {
                return false
            }
        } else {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }


    private fun getOrCreateLocalId(): String {
        val savedId = storage.getItem("_marketap_local_id", stringAdapter)
        return if (savedId != null) {
            savedId
        } else {
            val newId = UUID.randomUUID().toString()
            storage.setItem("_marketap_local_id", newId, stringAdapter)
            newId
        }
    }

    override fun isDeviceReady(): Boolean {
        val isFirstOpen = storage.getItem("first_open", booleanAdapter)
        return isFirstOpen == true
    }

    override fun setFirstOpen(): Boolean {
        val isFirstOpen = storage.getItem("first_open", booleanAdapter)
        if (isFirstOpen == null) {
            storage.setItem("first_open", true, booleanAdapter)
            logger.d { "Device marked as first open" }
            return true
        }
        return false
    }


    override fun getDevice(): Device {

        val displayMetrics = Resources.getSystem().displayMetrics
        val screen = Screen(
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            pixelRatio = displayMetrics.density
        )

        return Device(
            gaid = storage.getItem("gaid", stringAdapter),
            appSetId = storage.getItem("app_set_id", stringAdapter),
            appLocalId = getOrCreateLocalId(),
            token = token,
            brand = Build.BRAND,
            screen = screen,
            maxTouchPoints = getMaxTouchPoints().toInt()
        )
    }

    private fun getMaxTouchPoints(): Long {
        return storage.getItem("max_touch_points", longAdapter) ?: 1
    }
}