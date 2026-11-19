package com.marketap.sdk.domain.service.inapp

/** 규칙 태그가 비어 있으면 모든 캠페인에 해당한다(서버 `FrequencyCapServiceImpl`과 같은 판정). */
internal fun ruleTagsIntersect(ruleTagIds: List<String>, candidateTagIds: List<String>): Boolean =
    ruleTagIds.isEmpty() || ruleTagIds.any { it in candidateTagIds }
