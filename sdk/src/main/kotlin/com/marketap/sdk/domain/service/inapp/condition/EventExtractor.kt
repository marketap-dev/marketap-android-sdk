package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.ExtractionStrategy
import com.marketap.sdk.model.internal.inapp.Path
import com.marketap.sdk.utils.toDate

object EventExtractor {
    private fun Any.asAny(dataType: DataType): Any? {
        return when (dataType) {
            DataType.STRING -> this as? String
            DataType.INT -> (this as? Number)?.toInt()
            DataType.BIGINT -> (this as? Number)?.toLong()
            DataType.DOUBLE -> (this as? Number)?.toDouble()
            DataType.BOOLEAN -> this as? Boolean
            DataType.DATETIME -> (this as? String)?.toDate()
            DataType.DATE -> (this as? String)
            DataType.STRING_ARRAY -> (this as? List<*>)?.mapNotNull { it as? String }
            DataType.OBJECT -> TODO()
            DataType.OBJECT_ARRAY -> TODO()
        }
    }

    fun extract(
        target: Map<String, Any>?,
        strategy: ExtractionStrategy
    ): List<Any?> {
        return when (strategy.propertySchema.path) {
            Path.ITEM -> (
                    target?.get("mkt_items") as? List<Map<String, Any>>?
                    )?.map {
                    it[strategy.propertySchema.name]
                        ?.asAny(strategy.propertySchema.dataType)
                } ?: emptyList()

            else -> listOf(
                target?.get(strategy.propertySchema.name)
                    ?.asAny(strategy.propertySchema.dataType)
            )
        }
    }
}