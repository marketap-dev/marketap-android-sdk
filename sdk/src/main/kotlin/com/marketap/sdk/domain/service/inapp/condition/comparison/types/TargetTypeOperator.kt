package com.marketap.sdk.domain.service.inapp.condition.comparison.types

class TargetTypeOperator<T>(private val typeBlock: (targets: Array<out Any>) -> T) {
    operator fun <S> invoke(vararg targets: Any, operationBlock: (T) -> S): S {
        val target = typeBlock(targets)
        return operationBlock(target)
    }
}
