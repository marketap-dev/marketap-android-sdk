package com.marketap.sdk.model.internal.inapp


enum class TaxonomyOperator {
    EQUAL,
    NOT_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    BETWEEN,
    NOT_BETWEEN,
    LIKE,
    NOT_LIKE,
    IS_NULL,
    IS_NOT_NULL,
    YEAR_EQUAL,
    MONTH_EQUAL,
    YEAR_MONTH_EQUAL,
    CONTAINS,
    NOT_CONTAINS,
    ANY,
    NONE,
    BEFORE, // 전이다. ( N일전 )
    PAST,  // 이상 지났다 ( ~N일전 )
    WITHIN_PAST, // 지난 기간 이내이다. ( N일 ~ 최근 )
    AFTER, // 이후이다. ( N일후 )
    REMAINING, // 남은 기간이다. ( N일후 ~ )
    WITHIN_REMAINING, // 남은 기간 이내이다. ( 최근 ~ N일후 )
    ;
}