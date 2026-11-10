package com.marketap.sdk.client.inapp

import com.marketap.sdk.domain.service.inapp.monotonicNowMs
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.mapAdapter
import java.util.concurrent.atomic.AtomicLong

private val nextSessionId = AtomicLong()

/** 표시 요청 하나의 콜백 묶음. 노출과 숨김은 한 번만 전달하고, 숨긴 뒤에는 클릭을 받지 않는다. */
internal class InAppSession(
    private val onShow: () -> Unit,
    private val onClick: (String) -> String,
    private val onHide: (HideType) -> Unit,
    private val onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
    private val onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    val origin: InAppOrigin? = null,
    val closeOnPageLeave: Boolean = false,
) : InAppCallback {
    /** 팝업 화면이 자기가 그리는 표시 요청을 가리키는 값. 표시 요청마다 다르다. */
    val id: Long = nextSessionId.incrementAndGet()

    /** 화면에 나타난 단조 시각(ms). 아직 나타나지 않았으면 null. */
    @Volatile
    var shownAtMs: Long? = null
        private set

    @Volatile
    private var hidden = false

    @Synchronized
    fun shown(atMs: Long = monotonicNowMs()) {
        if (shownAtMs != null || hidden) return
        shownAtMs = atMs
        onShow()
    }

    @Synchronized
    override fun onHide(hideType: HideType) {
        if (hidden) return
        hidden = true
        onHide.invoke(hideType)
    }

    override fun onClick(locationId: String): String? {
        if (hidden) return null
        return onClick.invoke(locationId)
    }

    override fun onTrack(eventName: String, eventPropertiesJson: String) {
        try {
            val eventProperties = eventPropertiesJson.deserialize(mapAdapter<String, Any>())
            onTrack.invoke(eventName, eventProperties)
        } catch (error: Exception) {
            logger.e(error) { "Error parsing event properties JSON: $eventPropertiesJson" }
        }
    }

    override fun onSetUserProperties(userPropertiesJson: String) {
        try {
            val userProperties = userPropertiesJson.deserialize(mapAdapter<String, Any>())
            onSetUserProperties.invoke(userProperties)
        } catch (error: Exception) {
            logger.e(error) { "Error parsing user properties JSON: $userPropertiesJson" }
        }
    }
}
