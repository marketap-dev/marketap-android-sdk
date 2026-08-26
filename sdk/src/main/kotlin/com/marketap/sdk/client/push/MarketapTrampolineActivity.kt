package com.marketap.sdk.client.push

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.CAMPAIGN_KEY
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.IS_NOTIFICATION_FROM_MARKETAP
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.NOTIFICATION_DEEP_LINK_KEY
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.NOTIFICATION_URL_KEY
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.utils.logger

class MarketapTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d { "MarketapTrampolineActivity(Push handling activity) onCreate() called" }

        // 알림 탭은 어떤 경우에도 호스트 앱을 죽이면 안 된다. 여기서 도는 코드에는
        // 호스트 앱이 등록한 클릭 핸들러와 Intent 역직렬화가 섞여 있어 SDK 가
        // 통제할 수 없는 예외가 나올 수 있다.
        try {
            handleNotificationOpen()
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle Marketap notification open" }
            launchSafely(launcherIntent())
        }

        quit()
    }

    private fun handleNotificationOpen() {
        if (intent.getBooleanExtra(IS_NOTIFICATION_FROM_MARKETAP, false)) {
            val deepLink = intent.getStringExtra(NOTIFICATION_DEEP_LINK_KEY)
            val url = intent.getStringExtra(NOTIFICATION_URL_KEY)
            val data: DeliveryData? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(CAMPAIGN_KEY, DeliveryData::class.java)
            } else {
                intent.getSerializableExtra(CAMPAIGN_KEY) as? DeliveryData?
            }
            if (data != null) {
                PushTracker.trackClick(this, data)
                if (CustomHandlerStore.maybeHandleClick(
                        this, MarketapClickEvent(
                            MarketapCampaignType.PUSH,
                            data.campaignId,
                            deepLink ?: url
                        )
                    )
                ) {
                    logger.d { "Push Click handled by custom click handler" }
                    return
                }
            }

            // 딥링크 or 앱 런치
            val target = deepLink?.takeIf { it.isNotEmpty() } ?: url?.takeIf { it.isNotEmpty() }

            logger.d { "Launching Marketap notification with deepLink: $deepLink, url: $url" }
            if (target != null) {
                // 딥링크가 실패해도 앱은 열어준다. 콘솔에 잘못된 스킴이 들어갔거나
                // 사용자의 앱 버전에 그 화면이 아직 없을 수 있다.
                if (!launchSafely(viewIntent(target))) {
                    launchSafely(launcherIntent())
                }
            } else {
                launchSafely(launcherIntent())
            }
        } else {
            logger.w { "MarketapTrampolineActivity launched without valid Marketap notification data" }
            launchSafely(launcherIntent())
        }
    }

    private fun viewIntent(target: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** 런처 액티비티가 없는 앱도 있어서 nullable. */
    private fun launcherIntent(): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /**
     * 알림 탭이 호스트 앱을 죽이면 안 된다.
     *
     * 처리할 액티비티가 없는 딥링크는 startActivity 에서 ActivityNotFoundException 을
     * 던진다. 콘솔에서 지정하는 값이라 SDK 가 통제할 수 없고, 사용자 앱 버전에 아직
     * 없는 화면일 수도 있다. 예전에는 이게 그대로 onCreate 밖으로 나가 크래시가 났고,
     * quit() 도 못 돌아 알림이 트레이에 남았다.
     */
    private fun launchSafely(intent: Intent?): Boolean {
        if (intent == null) {
            logger.w { "No intent to launch for Marketap notification" }
            return false
        }
        return try {
            startActivity(intent)
            true
        } catch (t: Throwable) {
            logger.e(t) { "Failed to launch Marketap notification target: ${intent.data}" }
            false
        }
    }

    private fun quit() {
        val id = intent.getIntExtra(
            MarketapNotificationOpenHandler.NOTIFICATION_ID_KEY,
            -1
        )
        if (id != -1) {
            NotificationManagerCompat.from(this).cancel(id)
        }

        // 안전하게 finish() (onPause() 보장 안 될 수 있어서 딜레이 후 종료)
        window.decorView.postDelayed({
            finish()
        }, 200)
    }


    override fun onPause() {
        super.onPause()
        finish()
    }
}