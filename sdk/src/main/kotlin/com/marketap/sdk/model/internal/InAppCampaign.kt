package com.marketap.sdk.model.internal

import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.Layout
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.serialize
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
internal data class InAppCampaign(
    val id: String,
    val layout: Layout,

    val triggerEventCondition: EventTriggerCondition,
    val priority: String,
    val html: String?,
    val updatedAt: String,
    val displaySlot: String? = null,
    /** 앱은 쓰지 않는다. 웹 화면에 위임할 때 웹 SDK 로 그대로 넘긴다. */
    val floatingScope: String? = null,
) {
    @Suppress("UNCHECKED_CAST")
    fun toMap(): Map<String, Any?> {
        return try {
            val json = this.serialize(adapter())
            val moshi = Moshi.Builder().build()
            val mapAdapter = moshi.adapter<Map<String, Any?>>(Map::class.java)
            mapAdapter.fromJson(json) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

internal val InAppCampaign.slot: InAppDisplaySlot get() = InAppDisplaySlot.of(displaySlot)
