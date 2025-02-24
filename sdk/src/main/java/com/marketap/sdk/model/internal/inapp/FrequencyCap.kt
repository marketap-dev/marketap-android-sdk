package com.marketap.sdk.model.internal.inapp

import com.google.gson.annotations.SerializedName


data class FrequencyCap(
    val limit: Int,


    @SerializedName("duration_minutes")
    val durationMinutes: Int
)


