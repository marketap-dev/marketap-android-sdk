package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.utils.MarketapDate
import java.util.Date

class ValueComparatorImpl : ValueComparator {
    override fun compare(
        value: Any?,
        dataType: DataType,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        if (value == null) {
            return when (operator) {
                TaxonomyOperator.IS_NULL -> true
                TaxonomyOperator.IS_NOT_NULL -> false
                else -> false
            }
        }
        return when (dataType) {
            DataType.STRING -> TypeConverter<String>(value)?.let {
                StringComparator().compare(it, targetValues, operator)
            }

            DataType.INT,
            DataType.BIGINT -> TypeConverter<Long>(value)?.let {
                LongComparator().compare(it, targetValues, operator)
            }

            DataType.DOUBLE -> TypeConverter<Double>(value)?.let {
                DoubleComparator().compare(it, targetValues, operator)
            }

            DataType.BOOLEAN -> TypeConverter<Boolean>(value)?.let {
                BooleanComparator().compare(it, targetValues, operator)
            }

            DataType.DATETIME -> TypeConverter<Date>(value)?.let {
                DateComparator().compare(it, targetValues, operator)
            }

            DataType.DATE -> TypeConverter<MarketapDate>(value)?.let {
                MarketapDateComparator().compare(it, targetValues, operator)
            }

            DataType.STRING_ARRAY -> TypeConverter<List<String>>(value)?.let {
                StringArrayComparator().compare(it, targetValues, operator)
            }

            DataType.OBJECT -> TODO()
            DataType.OBJECT_ARRAY -> TODO()
        } ?: false
    }

}