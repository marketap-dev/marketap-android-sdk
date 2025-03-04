package com.marketap.sdk.presentation

import androidx.appcompat.app.AppCompatActivity

class ActivityManager {
    private var activity: AppCompatActivity? = null

    fun setCurrentActivity(activity: AppCompatActivity?) {
        this.activity = activity
    }

    fun getCurrentActivity(): AppCompatActivity? {
        return activity
    }
}