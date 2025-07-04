package com.marketap.android

import android.app.Application
import com.marketap.sdk.Marketap

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Marketap.initialize(this, "kx43pz7", true)
    }
}