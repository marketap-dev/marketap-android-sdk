package com.marketap.sdk.model.internal

import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.model.internal.inapp.Layout
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InAppCampaign(
    val id: String,
    val layout: Layout,

    val triggerEventCondition: EventTriggerCondition,
    val priority: String,
    val html: String
)