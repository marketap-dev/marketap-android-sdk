package com.marketap.sdk.utils

import android.util.Log


private const val MAIN_TAG = "MarketapSDK"
internal val logger = object : MarketapLogger {
    override fun d(tag: String, message: String) {
        Log.d(MAIN_TAG, "$tag: $message")
    }

    override fun e(tag: String, message: String, exception: Exception?) {
        Log.e(MAIN_TAG, "$tag: $message", exception)
    }

    override fun i(tag: String, message: String) {
        Log.i(MAIN_TAG, "$tag: $message")
    }

    override fun w(tag: String, message: String) {
        Log.w(MAIN_TAG, "$tag: $message")
    }
}

interface MarketapLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, exception: Exception? = null)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
}