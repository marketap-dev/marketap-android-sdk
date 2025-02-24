package com.marketap.android

import android.app.Application
import com.marketap.sdk.Marketap.marketap

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        marketap.initialize(this, "kx43pz7")
    }
}