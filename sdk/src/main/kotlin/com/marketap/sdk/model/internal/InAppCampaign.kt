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
    /** 플로팅의 화면 이동 값. `PAGE` 면 "이 페이지에서만", 그 밖의 값과 값 없음은 "닫을 때까지 유지". */
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
