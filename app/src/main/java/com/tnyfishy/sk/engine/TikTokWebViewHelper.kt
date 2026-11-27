package com.tnyfishy.sk.engine

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.tnyfishy.sk.AppConstants

object TikTokWebViewHelper {

    @SuppressLint("SetJavaScriptEnabled")
    fun configureForLogin(webView: WebView, userAgent: String = AppConstants.DESKTOP_CHROME_UA) {
        // Software layer bypasses AdrenoVK Dawn Vulkan shader pipeline failures on HyperOS/Android 16+
        // that otherwise render the interactive login WebView completely black.
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webView.setBackgroundColor(Color.WHITE)
        webView.onResume()
        webView.resumeTimers()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgent
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = true
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureForHeadlessAutomation(webView: WebView, userAgent: String, density: Float) {
        webView.resumeTimers()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgent
            setSupportZoom(false)
            builtInZoomControls = false
            useWideViewPort = false
            loadWithOverviewMode = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.setInitialScale(100)

        // With setInitialScale(100) and useWideViewPort=false, WebView layout pixels map 1:1 to CSS pixels.
        // Use a realistic 1280x700 desktop viewport so TikTok's inbox sidebar (~580px tall) overflows
        // immediately on page 1 (~9 chats) and triggers virtualized infinite-scroll pagination properly.
        val widthPx = 1280
        val heightPx = 700
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, widthPx, heightPx)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        cookieManager.flush()
    }

    fun hasValidSessionCookie(): Boolean {
        return runCatching {
            val cm = CookieManager.getInstance()
            val c1 = cm.getCookie("https://www.tiktok.com").orEmpty()
            val c2 = cm.getCookie("https://tiktok.com").orEmpty()
            c1.contains("sessionid=") || c2.contains("sessionid=")
        }.getOrDefault(false)
    }

    fun flushCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }

    fun clearAllCookies() {
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
    }

    /**
     * Handles non-http(s) custom schemes (snssdk://, intent://, etc.) during TikTok login
     * to prevent ERR_UNKNOWN_URL_SCHEME white-screen crashes, extracting embedded params_url.
     * Returns extracted http(s) URL to reload, or null if simply blocked.
     */
    fun extractFallbackUrlFromCustomScheme(rawUrl: String): String? {
        val key = "params_url="
        val idx = rawUrl.indexOf(key)
        if (idx < 0) return null
        val rest = rawUrl.substring(idx + key.length).substringBefore('&')
        val decoded = runCatching { Uri.decode(rest) }.getOrNull() ?: return null
        return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    fun tearDown(webView: WebView?) {
        webView ?: return
        runCatching {
            webView.stopLoading()
            webView.onPause()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }
}
