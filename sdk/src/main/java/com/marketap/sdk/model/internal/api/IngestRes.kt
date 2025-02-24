package com.marketap.sdk.model.internal.api

import com.google.gson.annotations.SerializedName


data class IngestRes(
    @SerializedName("is_success")
    val isSuccess: Boolean
)
