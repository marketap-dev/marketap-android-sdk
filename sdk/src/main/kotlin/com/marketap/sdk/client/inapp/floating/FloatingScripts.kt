package com.marketap.sdk.client.inapp.floating

internal object FloatingScripts {

    /** 플로팅 웹뷰의 JS 브리지 이름. 팝업 웹뷰의 `MarketapSDK` 와 겹치지 않게 따로 둔다. */
    const val BRIDGE_NAME = "MarketapFloating"

    /**
     * 템플릿 공개 접기 함수 `window.marketapCollapse` 가 있으면 그것을 쓴다. 없는 템플릿은 Escape 키,
     * 그래도 안 접히면 CLOSE 버튼, 그다음 바의 즉시 접기 순으로 시도한다.
     */
    val COLLAPSE_ANIMATED = """
        (function () {
          var w = document.getElementById("marketap-content-wrapper");
          if (!w || w.getAttribute("data-mkt-state") !== "expanded") return;
          if (typeof window.marketapCollapse === "function") { window.marketapCollapse({ animated: true }); return; }
          document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
          if (w.getAttribute("data-mkt-state") !== "expanded" || w.hasAttribute("data-mkt-closing")) return;
          var button = document.querySelector('[data-bottom-button-type="CLOSE"]');
          if (button) { button.click(); return; }
          var bar = document.querySelector("[data-mkt-bar]");
          if (bar && bar._mktReset) bar._mktReset();
        })();
    """.trimIndent()

    /** 화면을 옮길 때 쓰는 애니메이션 없는 접기. */
    val COLLAPSE_INSTANT = """
        (function () {
          var w = document.getElementById("marketap-content-wrapper");
          if (!w || w.getAttribute("data-mkt-state") !== "expanded") return;
          var bar = document.querySelector("[data-mkt-bar]");
          if (typeof window.marketapCollapse === "function") window.marketapCollapse({ animated: false });
          else if (bar && bar._mktReset) bar._mktReset();
          else document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
          if (window.__mktFloatingSync) window.__mktFloatingSync();
        })();
    """.trimIndent()

    fun applySafeArea(topPx: Int, bottomPx: Int): String =
        "if(window._marketap_native){window._marketap_native.topSafeArea=$topPx/window.devicePixelRatio;" +
            "window._marketap_native.bottomSafeArea=$bottomPx/window.devicePixelRatio;}" +
            "if(window.marketapApplySafeArea){window.marketapApplySafeArea();}" +
            "if(window.__mktFloatingSync){window.__mktFloatingSync();setTimeout(window.__mktFloatingSync,300);}"

    /**
     * 템플릿 HTML 을 플로팅 문서로 감싼다.
     * @param timerStartAtMillis 노출 시작 시각(epoch ms). 템플릿 타이머의 기준이다.
     */
    fun buildDocument(html: String, topSafeAreaPx: Int, bottomSafeAreaPx: Int, timerStartAtMillis: Long): String {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;padding:0;background:transparent}</style>
            <script>
            window._marketap_native = {
                hide: function (hideType) { if (window.$BRIDGE_NAME) $BRIDGE_NAME.hideCampaign(String(hideType)); },
                click: function (locationId, url) { if (window.$BRIDGE_NAME) $BRIDGE_NAME.trackClick(String(locationId), url == null ? "" : String(url)); },
                track: function (eventName, json) { if (window.$BRIDGE_NAME) $BRIDGE_NAME.trackEvent(String(eventName), String(json)); },
                setUserProperties: function (json) { if (window.$BRIDGE_NAME) $BRIDGE_NAME.setUserProperties(String(json)); },
                topSafeArea: $topSafeAreaPx / window.devicePixelRatio,
                bottomSafeArea: $bottomSafeAreaPx / window.devicePixelRatio,
                timerStartAt: $timerStartAtMillis
            };
            </script>
            </head>
            <body>
            $html
            <script>document.dispatchEvent(new Event("ForceDOMContentLoaded"));</script>
            <script>$WATCHER</script>
            </body>
            </html>
        """.trimIndent()
    }

    private val WATCHER = """
        (function () {
          function visibleRect() {
            var bar = document.querySelector(".marketap-bar-row");
            if (bar) {
              var r = bar.getBoundingClientRect();
              if (r.width === 0 || r.height === 0) return null;
              return [r.left, r.top, r.right, r.bottom];
            }
            var c = document.getElementById("marketap-content-container");
            if (!c) return null;
            var left = 0, top = 0;
            for (var n = c; n; n = n.offsetParent) { left += n.offsetLeft; top += n.offsetTop; }
            return [left, top, left + c.offsetWidth, top + c.offsetHeight];
          }
          function sync() {
            var w = document.getElementById("marketap-content-wrapper");
            var state = w ? (w.getAttribute("data-mkt-state") || "") : "";
            var r = visibleRect();
            if (window.$BRIDGE_NAME) $BRIDGE_NAME.onLayout(state, r ? r.join(",") : "", window.innerWidth);
          }
          window.__mktFloatingSync = sync;
          var wrapper = document.getElementById("marketap-content-wrapper");
          if (wrapper && window.MutationObserver) {
            new MutationObserver(sync).observe(wrapper, { attributes: true, attributeFilter: ["data-mkt-state", "style", "class"] });
          }
          var visible = document.querySelector(".marketap-bar-row") || document.getElementById("marketap-content-container");
          if (visible && window.ResizeObserver) new ResizeObserver(sync).observe(visible);
          window.addEventListener("resize", sync);
          document.addEventListener("transitionend", sync);
          document.addEventListener("animationend", sync);
          sync();
          setTimeout(sync, 300);
          setTimeout(sync, 1000);
        })();
    """.trimIndent()
}
