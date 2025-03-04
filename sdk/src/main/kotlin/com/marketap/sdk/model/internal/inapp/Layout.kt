package com.marketap.sdk.model.internal.inapp





data class Layout(
    val layoutType: String,

    val layoutSubType: String,

    val orientations: List<String>
)