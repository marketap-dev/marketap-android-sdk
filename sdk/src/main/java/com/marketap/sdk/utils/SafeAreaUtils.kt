package com.marketap.sdk.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.WindowInsets

object SafeAreaUtils {

    // ✅ 상단 Status Bar 높이 구하기 (Android 11 이상 안전한 방식)
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    fun getStatusBarHeight(activity: Activity?): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = activity?.window?.decorView?.rootWindowInsets
            insets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())?.top ?: return 0
        } else {
            // API 30 미만에서는 기존 방식 사용
            val resourceId =
                activity?.resources?.getIdentifier("status_bar_height", "dimen", "android")
                    ?: return 0
            if (resourceId > 0) activity.resources?.getDimensionPixelSize(resourceId) ?: 0 else 0
        }
    }

    // ✅ 하단 Navigation Bar 높이 구하기 (Android 11 이상 안전한 방식)
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    fun getNavigationBarHeight(activity: Activity?): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = activity?.window?.decorView?.rootWindowInsets ?: return 0
            val barInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            barInsets.bottom
        } else {
            val resourceId =
                activity?.resources?.getIdentifier("navigation_bar_height", "dimen", "android")
                    ?: return 0
            if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else 0
        }
    }
}