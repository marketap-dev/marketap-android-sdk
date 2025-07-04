package com.marketap.sdk.utils

import android.util.Log
import com.marketap.sdk.model.external.MarketapLogLevel


private const val MAIN_TAG = "MarketapSDK"
internal inline val <reified T> T.logger: MarketapLogger
    get() {
        return object : MarketapLogger {
            override fun v(description: String, argument: String?) {
                if (!MarketapLogLevel.VERBOSE.isEnabled()) return
                Log.v(
                    MAIN_TAG,
                    "[${T::class.java.name}]: $description${argument?.let { "($it)" } ?: ""}"
                )
            }

            override fun d(description: String, argument: String?) {
                if (!MarketapLogLevel.DEBUG.isEnabled()) return
                Log.d(
                    MAIN_TAG,
                    "[${T::class.java.name}]: $description${argument?.let { "($it)" } ?: ""}")
            }

            override fun e(description: String, argument: String?, exception: Exception?) {
                if (!MarketapLogLevel.ERROR.isEnabled()) return
                Log.e(
                    MAIN_TAG,
                    "[${T::class.java.name}]: $description${argument?.let { "($it)" } ?: ""}",
                    exception
                )
            }

            override fun i(description: String, argument: String?) {
                if (!MarketapLogLevel.INFO.isEnabled()) return
                Log.i(
                    MAIN_TAG,
                    "[${T::class.java.name}]: $description${argument?.let { "($it)" } ?: ""}")
            }

            override fun w(description: String, argument: String?) {
                if (!MarketapLogLevel.WARN.isEnabled()) return
                Log.w(
                    MAIN_TAG,
                    "[${T::class.java.name}]: $description${argument?.let { "($it)" } ?: ""}")
            }
        }
    }

internal interface MarketapLogger {
    fun v(description: String, argument: String? = null) // verbose
    fun d(description: String, argument: String? = null) // debug
    fun i(description: String, argument: String? = null) // info
    fun w(description: String, argument: String? = null) // warn
    fun e(description: String, argument: String? = null, exception: Exception? = null) // error
}