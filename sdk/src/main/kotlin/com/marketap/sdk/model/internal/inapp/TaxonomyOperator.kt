package com.marketap.sdk.model.internal.inapp


/**
 * 조건 비교 연산자
 */
enum class TaxonomyOperator {
    /** 이다 */
    EQUAL,
    /** (이)가 아니다 */
    NOT_EQUAL,
    /** 보다 크다 */
    GREATER_THAN,
    /** 보다 크거나 같다 */
    GREATER_THAN_OR_EQUAL,
    /** 보다 작다 */
    LESS_THAN,
    /** 보다 작거나 같다 */
    LESS_THAN_OR_EQUAL,
    /** 중 하나다 */
    IN,
    /** (이)가 아니다 (복수) */
    NOT_IN,
    /** 의 사이값이다 (exclusive) */
    BETWEEN,
    /** 의 사이값이 아니다 */
    NOT_BETWEEN,
    /** 을(를) 포함한다 (case insensitive) */
    LIKE,
    /** 을(를) 포함하지 않는다 (case insensitive) */
    NOT_LIKE,
    /** 을(를) 포함하는 항목이 있다 (배열용, case insensitive) */
    ARRAY_LIKE,
    /** 을(를) 포함하는 항목이 없다 (배열용, case insensitive) */
    ARRAY_NOT_LIKE,
    /** 값이 없다 */
    IS_NULL,
    /** 값이 있다 */
    IS_NOT_NULL,
    /** 년이다 */
    YEAR_EQUAL,
    /** 월이다 */
    MONTH_EQUAL,
    /** 년월이다 */
    YEAR_MONTH_EQUAL,
    /** 을(를) 포함한다 (배열에 값 포함 여부) */
    CONTAINS,
    /** 을(를) 포함하지 않는다 (배열에 값 미포함 여부) */
    NOT_CONTAINS,
    /** 중 하나 이상 포함한다 (배열용) */
    ANY,
    /** 을(를) 모두 포함하지 않는다 (배열용) */
    NONE,
    /** N일 전이다 (정확히 N일 전) */
    BEFORE,
    /** N일 이상 지났다 */
    PAST,
    /** N일 이내로 지났다 (최근 N일 이내) */
    WITHIN_PAST,
    /** N일 후이다 (정확히 N일 후) */
    AFTER,
    /** N일 이상 남았다 */
    REMAINING,
    /** N일 이내로 남았다 */
    WITHIN_REMAINING,
    ;

    fun isNegativeOperator(): Boolean = when (this) {
        NOT_EQUAL, NOT_IN, NOT_BETWEEN, NOT_LIKE, ARRAY_NOT_LIKE,
        IS_NOT_NULL, NOT_CONTAINS, NONE -> true
        else -> false
    }

    fun aggregate(results: List<Boolean>): Boolean =
        if (results.isEmpty()) {
            false
        } else if (isNegativeOperator()) {
            results.all { it }
        } else {
            results.any { it }
        }
}