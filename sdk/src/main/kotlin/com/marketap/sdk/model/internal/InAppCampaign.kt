package com.marketap.sdk.model.internal

import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
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
) {
    /**
     * Map으로 변환 (Flutter 브릿지용)
     */
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