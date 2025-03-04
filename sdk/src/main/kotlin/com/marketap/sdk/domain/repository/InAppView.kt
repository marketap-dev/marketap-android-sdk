package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.inapp.HideType

interface InAppView {
    fun show(
        html: String,
        onShow: () -> Unit,
        onClick: (String) -> Unit,
        onHide: (HideType) -> Unit
    )
}