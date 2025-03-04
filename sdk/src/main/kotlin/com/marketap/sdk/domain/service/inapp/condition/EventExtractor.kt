package com.marketap.sdk.domain.service.inapp.condition

import com.google.gson.JsonElement
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.toDate
import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.ExtractionStrategy
import com.marketap.sdk.model.internal.inapp.Path
import com.marketap.sdk.utils.serializeToJson


object EventExtractor {
    private fun JsonElement.asAny(dataType: DataType): Any? {
        return when (dataType) {
            DataType.STRING -> asString
            DataType.INT -> asInt
            DataType.BIGINT -> asBigInteger
            DataType.DOUBLE -> asDouble
            DataType.BOOLEAN -> asBoolean
            DataType.DATETIME -> asString.toDate()
            DataType.DATE -> asString
            DataType.STRING_ARRAY -> asJsonArray.map { it.asString }
            DataType.OBJECT -> TODO()
            DataType.OBJECT_ARRAY -> TODO()
        }
    }

    fun extract(
        target: Map<String, Any>?,
        strategy: ExtractionStrategy
    ): List<Any?> {
        val convertedTarget = target?.serializeToJson()

        return when (strategy.propertySchema.path) {
            Path.ITEM -> convertedTarget?.get("mkt_items")?.asJsonArray?.map {
                it.asJsonObject.get(strategy.propertySchema.name)
                    ?.asAny(strategy.propertySchema.dataType)
            } ?: emptyList()

            else -> listOf(
                convertedTarget?.get(strategy.propertySchema.name)
                    ?.asAny(strategy.propertySchema.dataType)
            )
        }
    }
}