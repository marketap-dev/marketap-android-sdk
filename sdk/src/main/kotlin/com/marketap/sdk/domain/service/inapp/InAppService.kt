package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.MarketapWebBridge
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.utils.logger
import java.util.UUID

/**
 * 상세 fetch 를 실제로 보내는 후보의 최대 개수. 서버가 노출을 확정하지 못해 빈 응답을
 * 주면 다음 후보로 넘어가는데, 그때마다 요청이 나가므로 상한을 둔다. html 이 이미 있는
 * 정적 렌더 캠페인은 서버를 안 타므로 이 예산을 깎지 않는다. (web SDK 와 동일한 정책)
 */
private const val MAX_FALLTHROUGH_FETCHES = 5

/**
 * 폴스루 전체의 시간 예산(ms).
 *
 * resolveCampaignHtml 은 runBlocking + withTimeoutOrNull(1000) 이라 **호출 스레드를**
 * fetch 당 최대 1초 붙잡는다. onEvent 는 track() 을 부른 스레드(메인일 수 있음)에서 그대로
 * 도므로, 상한 없이 후보를 이어가면 최대 5초를 블록해 ANR 을 낸다. 예산을 넘기면 멈춘다.
 * (web SDK 는 이 값이 2000ms 다. Android 는 fetch 당 1초 블록이라 2회면 예산이 찬다.)
 */
private const val FALLTHROUGH_BUDGET_MS = 2000L

/**
 * 경과 시간 측정용 단조 시계(ms).
 *
 * 벽시계(System.currentTimeMillis)는 NTP 보정이나 사용자의 시간 변경으로 앞뒤로 튄다.
 * 뒤로 튀면 예산이 사실상 무한이 되고, 앞으로 튀면 첫 후보에서 바로 끊긴다. 기간을 재는 데는
 * 절대 시각이 아니라 단조 증가 값을 쓴다. (web SDK 의 performance.now 와 같은 이유)
 *
 * SystemClock.elapsedRealtime 이 아니라 System.nanoTime 을 쓴 이유: 이 파일은 도메인 계층이라
 * 안드로이드 프레임워크에 의존하지 않는다(순수 JVM 단위 테스트에서 그대로 돈다).
 */
private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000

internal class InAppService(
    private val campaignExposureService: CampaignExposing,
    private val eventConditionChecker: ConditionChecker,
    private val campaignFetchService: CampaignFetching,
    private val inAppView: InAppView
) {

    fun onEvent(
        event: IngestEventRequest,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        onEvent(event, fromWebBridge = false, onImpression, onClick, onTrack, onSetUserProperties)
    }

    fun onEvent(
        event: IngestEventRequest,
        fromWebBridge: Boolean,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        campaignFetchService.useCampaigns { campaigns ->
            // 우선순위 순 후보 전체를 본다. 예전에는 find() 로 1순위 하나만 잡아서, 그 캠페인의
            // 상세 fetch 가 빈 응답이면(쿠폰 발급 실패·타겟팅 탈락 등 서버가 노출을 확정 못 한 경우)
            // 뒤에 뜰 수 있는 캠페인이 있어도 화면에 아무것도 안 떴다. (web SDK 와 동일한 수정)
            val candidates = campaigns.filter { campaign ->
                if (!eventConditionChecker.checkCondition(
                        campaign.triggerEventCondition.condition,
                        event.name,
                        event.properties
                    )
                ) {
                    logger.v {
                        "Campaign ${campaign.id} does not match event condition for event ${event.name}"
                    }
                    return@filter false
                }
                if (campaignExposureService.isCampaignHidden(campaign.id)) {
                    logger.v { "Campaign ${campaign.id} is hidden" }
                    return@filter false
                }

                campaign.triggerEventCondition.frequencyCap?.let { frequencyCap ->
                    if (campaignExposureService.hasReachedImpressionLimit(
                            campaign.id,
                            frequencyCap.durationMinutes,
                            frequencyCap.limit
                        )
                    ) {
                        logger.v {
                            "Campaign ${campaign.id} has reached frequency cap limit"
                        }
                        return@filter false
                    }
                }
                logger.v { "Campaign ${campaign.id} matches event condition for event ${event.name}" }
                true
            }

            val deadline = monotonicNowMs() + FALLTHROUGH_BUDGET_MS
            var fetches = 0
            for (campaign in candidates) {
                // html 이 비어 있는 후보만 상세 fetch 를 탄다(resolveCampaignHtml 의 분기와 같은 조건).
                // 정적 렌더 캠페인은 서버를 안 타므로 요청 예산에서 뺀다.
                val needsFetch = campaign.html == null
                if (needsFetch) {
                    if (fetches >= MAX_FALLTHROUGH_FETCHES) {
                        logger.v { "Reached fetch budget ($MAX_FALLTHROUGH_FETCHES), stopping fallthrough" }
                        break
                    }
                    // fetch 는 스레드를 최대 1초 블록한다. 남은 예산이 없으면 시작조차 안 한다.
                    if (monotonicNowMs() >= deadline) {
                        logger.v { "Reached time budget (${FALLTHROUGH_BUDGET_MS}ms), stopping fallthrough" }
                        break
                    }
                    fetches++
                }

                val resolvedCampaign = campaignFetchService.resolveCampaignHtml(campaign, event)
                    ?: continue

                // 웹브릿지에서 온 이벤트이고 활성 웹브릿지가 있으면 웹으로 캠페인 전달
                val shouldDelegateToWeb = fromWebBridge && MarketapWebBridge.hasActiveWebBridge()

                if (shouldDelegateToWeb) {
                    handleCampaignForWeb(resolvedCampaign)
                } else {
                    handleCampaign(resolvedCampaign, onImpression, onClick, onTrack, onSetUserProperties)
                }
                break
            }
        }
    }

    private fun handleCampaignForWeb(
        targetCampaign: InAppCampaign
    ) {
        logger.d { "Delegating in-app campaign to web: ${targetCampaign.id} with layout type: ${targetCampaign.layout.layoutType}" }

        // 빈도 제한을 위한 노출 기록 (이벤트 전송은 웹에서 impression이 올 때 수행)
        campaignExposureService.recordImpression(targetCampaign.id)

        // 웹으로 캠페인 전달
        val messageId = UUID.randomUUID().toString()
        MarketapWebBridge.sendCampaignToActiveWeb(targetCampaign, messageId)
    }

    private fun handleCampaign(
        targetCampaign: InAppCampaign,
        onImpression: (campaign: InAppCampaign) -> Unit,
        onClick: (campaign: InAppCampaign, locationId: String) -> Unit,
        onTrack: (campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        logger.d { "Showing in-app campaign: ${targetCampaign.id} with layout type: ${targetCampaign.layout.layoutType}" }
        val campaignHtml = targetCampaign.html ?: return

        inAppView.show(
            campaignHtml,
            {
                campaignExposureService.recordImpression(targetCampaign.id)
                onImpression(targetCampaign)
                logger.d { "Recorded impression for campaign: ${targetCampaign.id}" }
            },
            { locationId ->
                onClick(targetCampaign, locationId)
                logger.d { "Recorded click for campaign: ${targetCampaign.id} at location: $locationId" }
                targetCampaign.id
            },
            { hideType ->
                logger.d { "Hiding campaign: ${targetCampaign.id} with hide type: $hideType" }
                hideCampaignByType(targetCampaign.id, hideType)
            },
            { eventName, properties ->
                onTrack(targetCampaign, eventName, properties)
            },
            onSetUserProperties,
        )
    }

    /**
     * 캠페인 숨김 처리 (웹브릿지에서 호출)
     */
    fun hideCampaign(campaignId: String, hideType: HideType) {
        logger.d { "Hiding campaign from web bridge: $campaignId with hide type: $hideType" }
        hideCampaignByType(campaignId, hideType)
    }

    private fun hideCampaignByType(campaignId: String, hideType: HideType) {
        when (hideType) {
            HideType.HIDE_FOR_ONE_DAY -> campaignExposureService.hideCampaign(
                campaignId, System.currentTimeMillis() + 1000 * 60 * 60 * 24
            )

            HideType.HIDE_FOR_SEVEN_DAYS -> campaignExposureService.hideCampaign(
                campaignId, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7
            )

            HideType.HIDE_FOREVER -> campaignExposureService.hideCampaign(
                campaignId,
                System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10
            )

            HideType.CLOSE -> {}
        }
    }
}
