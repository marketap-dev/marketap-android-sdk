package com.marketap.sdk.client.inapp.floating

internal enum class ResumeAction {
    IGNORE,

    /** 다른 화면으로 옮긴다. 펼쳐져 있으면 접고 옮기며, 접힌 바는 스냅샷을 남긴다. */
    MOVE,

    /** 회전 등으로 다시 만들어진 같은 화면에 상태 그대로 다시 붙인다. */
    RESTORE,
}

internal enum class DestroyAction {
    IGNORE,

    /** 구성 변경으로 파괴됐다. 떼어 두고 다시 만들어진 화면에 붙인다. */
    DETACH_FOR_RECREATE,

    /** 떼어 두고 다음에 앞에 오는 화면에 붙인다. */
    DETACH_AND_WAIT,

    CLOSE,
}

internal object FloatingAttachPolicy {

    /**
     * "닫을 때까지 유지"는 새로 앞에 온 화면으로 옮겨 붙는다.
     * @param stayOnHost 웹뷰에서 시작된 "이 페이지에서만". 옮기지 않고, 붙은 화면이 가려지면 따로 닫힌다.
     */
    fun onResumed(
        isHost: Boolean,
        recreatingClass: String?,
        activityClass: String,
        stayOnHost: Boolean = false,
    ): ResumeAction {
        if (isHost) return ResumeAction.IGNORE
        if (recreatingClass == activityClass) return ResumeAction.RESTORE
        if (stayOnHost) return ResumeAction.IGNORE
        return ResumeAction.MOVE
    }

    fun onDestroyed(isHost: Boolean, isChangingConfigurations: Boolean, stayOnHost: Boolean = false): DestroyAction {
        if (!isHost) return DestroyAction.IGNORE
        if (isChangingConfigurations) return DestroyAction.DETACH_FOR_RECREATE
        if (stayOnHost) return DestroyAction.CLOSE
        return DestroyAction.DETACH_AND_WAIT
    }

    /** 옮길 때 스냅샷은 접힌 바가 보이는 상태에서만 뜬다(펼친 화면 전체 비트맵은 뜨지 않는다). */
    fun shouldSnapshot(action: ResumeAction, hadHost: Boolean, expanded: Boolean, layout: FloatingLayout): Boolean =
        action == ResumeAction.MOVE && hadHost && !expanded && layout.ready && layout.area != null
}
