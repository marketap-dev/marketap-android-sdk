package com.marketap.sdk.utils

import android.util.Log
import com.marketap.sdk.model.external.MarketapLogLevel


private const val MAIN_TAG = "MarketapSDK"
internal inline val <reified T> T.logger: MarketapLogger
    get() {
        return object : MarketapLogger {
            override fun v(description: () -> String) {
                if (!MarketapLogLevel.VERBOSE.isEnabled()) return
                Log.v(
                    MAIN_TAG,
                    "[${T::class.java.name}]: ${description()}"
                )
            }

            override fun d(description: () -> String) {
                if (!MarketapLogLevel.DEBUG.isEnabled()) return
                Log.d(
                    MAIN_TAG,
                    "[${T::class.java.name}]: ${description()}"
                )
            }

            override fun e(exception: Throwable?, description: () -> String) {
                if (!MarketapLogLevel.ERROR.isEnabled()) return
                Log.e(
                    MAIN_TAG,
                    "[${T::class.java.name}]: ${description()}",
                    exception
                )
            }

            override fun i(description: () -> String) {
                if (!MarketapLogLevel.INFO.isEnabled()) return
                Log.i(
                    MAIN_TAG,
                    "[${T::class.java.name}]: ${description()}"
                )
            }

            override fun w(description: () -> String) {
                if (!MarketapLogLevel.WARN.isEnabled()) return
                Log.w(
                    MAIN_TAG,
                    "[${T::class.java.name}]: ${description()}"
                )
            }
        }
    }

internal interface MarketapLogger {
    fun v(description: () -> String) // verbose
    fun d(description: () -> String) // debug
    fun i(description: () -> String) // info
    fun w(description: () -> String) // warn
    fun e(exception: Throwable? = null, description: () -> String) // error
}
