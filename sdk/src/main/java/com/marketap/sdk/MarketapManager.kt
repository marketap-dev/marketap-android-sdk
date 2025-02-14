package com.marketap.sdk

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.icu.util.TimeZone
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal class MarketapManager(
    private val activity: Activity
) {
    private var token: String? = null
    private val sessionId: String = UUID.randomUUID().toString()

    private fun saveKey(key: String, value: String) {
        val sharedPreferences =
            activity.applicationContext.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(key, value).apply()
    }

    private fun fetchKey(key: String): String? {
        val sharedPreferences =
            activity.applicationContext.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString(key, null)
    }

    private fun getOrCreateLocalId(): String {
        val savedId = fetchKey("_marketap_local_id")
        if (savedId != null) {
            return savedId
        }
        val newId = UUID.randomUUID().toString()
        saveKey("_marketap_local_id", newId)
        return newId
    }

    private fun saveGAID(id: String) {
        saveKey("_marketa_gaid", id)
    }

    private fun saveAppSetId(id: String) {
        saveKey("_marketa_app_set_id", id)
    }

    fun setDeviceInfo(token: String?=null, gaid: String? = null, appSetId: String? = null) {
        token?.let { setToken(it) }
        gaid?.let { saveGAID(it) }
        appSetId?.let { saveAppSetId(it) }
    }

    private fun setToken(token: String) {
        this.token = token
    }

    fun getDeviceInfo(): JSONObject {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("app_local_id", getOrCreateLocalId())
            put("token", token)
            put("gaid", fetchKey("_marketa_gaid"))
            put("app_set_id", fetchKey("_marketa_app_set_id"))
            put("platform", "android")
            put("os", "Android ${Build.VERSION.RELEASE}")
            put("os_version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            put("library_version", "1.0.0") // 라이브러리 버전 (하드코딩된 예제)
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("app_version", "1.0.0") // 앱 버전 (하드코딩된 예제)
            put("app_build_number", "100") // 빌드 번호 (하드코딩된 예제)
            put("locale", Locale.getDefault().toString())

            val displayMetrics = Resources.getSystem().displayMetrics
            val screen = JSONObject().apply {
                put("width", displayMetrics.widthPixels)
                put("height", displayMetrics.heightPixels)
                put("pixel_ratio", displayMetrics.density)
            }
            put("screen", screen)
        }

        return json
    }

    private val webView: MarketapWebView = MarketapWebView(activity)
    private val rootView: FrameLayout = getRootView(activity)

    private fun getRootView(activity: Activity): FrameLayout {
        return activity.window.decorView.findViewById<FrameLayout>(android.R.id.content)!!.also {
            it.addView(
                webView, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }


    fun useMarketapCore(
        methodName: String,
        vararg args: Any?
    ) {
        webView.useMarketapCore(
            methodName,
            *(args.map {
                if (it is String) {
                    it
                } else {
                    (it?.serialize() ?: "undefined")
                }
            }.toTypedArray())
        )
    }
}