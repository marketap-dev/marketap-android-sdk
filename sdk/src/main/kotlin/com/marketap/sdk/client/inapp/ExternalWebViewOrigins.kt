package com.marketap.sdk.client.inapp

import com.marketap.sdk.model.internal.inapp.InAppOrigin
import java.util.concurrent.ConcurrentHashMap

/** 래퍼(Flutter·RN) 웹뷰마다 하나씩 두는 출처 목록. 웹뷰를 구분하지 않는 래퍼는 [DEFAULT_ID] 하나만 쓴다. */
internal class ExternalWebViewOrigins {
    @Volatile
    var commandCallback: ((String, Map<String, Any?>) -> Unit)? = null

    private val origins = ConcurrentHashMap<String, Origin>()

    fun originFor(webViewId: String): InAppOrigin = origins.getOrPut(webViewId) { Origin(webViewId) }

    fun attach(webViewId: String) {
        val origin = origins.getOrPut(webViewId) { Origin(webViewId) }
        origin.attached = true
        origin.detached = false
    }

    /** 해제된 웹뷰의 출처를 돌려준다. 등록된 적이 없으면 null. 기본 출처는 목록에 남긴다. */
    fun detach(webViewId: String): InAppOrigin? {
        val origin = (if (webViewId == DEFAULT_ID) origins[webViewId] else origins.remove(webViewId)) ?: return null
        origin.attached = false
        origin.detached = true
        return origin
    }

    fun existing(webViewId: String): InAppOrigin? = origins[webViewId]

    private inner class Origin(private val webViewId: String) : InAppOrigin {
        @Volatile
        var attached = false

        @Volatile
        var detached = false

        override fun canOpenUrl(): Boolean = commandCallback != null && attached

        override val isGone: Boolean get() = detached

        override fun openUrl(url: String) {
            val payload = if (webViewId == DEFAULT_ID) mapOf("url" to url) else mapOf("url" to url, "webViewId" to webViewId)
            commandCallback?.invoke(COMMAND_OPEN_URL, payload)
        }
    }

    companion object {
        const val DEFAULT_ID = ""
        const val COMMAND_OPEN_URL = "openUrl"
    }
}
