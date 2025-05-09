package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class Layout(
    val layoutType: String,

    val layoutSubType: String,

    val orientations: List<String>
)