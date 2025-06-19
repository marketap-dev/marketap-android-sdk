package com.marketap.android

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.marketap.sdk.Marketap

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Marketap.initialize(this, "kx43pz7", true)
        Marketap.setClickHandler {
            if (it.url != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            } else {
                startActivity(packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            }
        }
    }
}