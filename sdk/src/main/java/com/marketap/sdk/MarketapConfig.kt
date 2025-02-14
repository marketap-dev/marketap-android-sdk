package com.marketap.sdk

import android.app.Activity

data class MarketapConfig(
    val projectId: String,
    val activity: Activity,
    val debug: Boolean = false
)