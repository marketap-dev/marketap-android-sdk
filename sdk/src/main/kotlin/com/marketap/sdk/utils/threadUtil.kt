package com.marketap.sdk.utils

import android.os.Handler
import android.os.Looper

fun postToMainThread(runnable: Runnable) {
    Handler(Looper.getMainLooper()).post(runnable)
}