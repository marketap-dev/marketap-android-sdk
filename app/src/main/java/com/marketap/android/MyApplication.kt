package com.marketap.android

import android.app.Application
import com.marketap.sdk.Marketap
import com.marketap.sdk.model.external.MarketapLogLevel

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Marketap.setLogLevel(MarketapLogLevel.VERBOSE)
        Marketap.initialize(this, "kx43pz7")
    }
}