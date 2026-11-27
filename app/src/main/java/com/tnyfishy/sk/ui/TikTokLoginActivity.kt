package com.tnyfishy.sk.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.engine.TikTokAccountFetcher
import com.tnyfishy.sk.engine.TikTokWebViewHelper
import com.tnyfishy.sk.service.StreakScheduler
import com.tnyfishy.sk.ui.theme.StreakSaverTheme
import kotlinx.coroutines.launch

/**
 * Dedicated native Activity hosting WebView directly inside a LinearLayout (outside Compose AndroidView).
 * Eliminates Compose AndroidView + LAYER_TYPE_SOFTWARE vertical viewport squashing and ensures
 * crisp native scrolling, IME resize, and cookie capture for TikTok login.
 */
class TikTokLoginActivity : ComponentActivity() {

    private lateinit var repo: StreakRepository
    private var webView: WebView? = null
    private var completed = false

    private var loadProgressState by mutableIntStateOf(0)
    private var isLoadingState by mutableStateOf(true)
    private var subtitleState by mutableStateOf("Đăng nhập bằng tài khoản TikTok của bạn")

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = StreakRepository.getInstance(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        val topBarView = ComposeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setContent {
                StreakSaverTheme(dynamicColor = repo.getUseDynamicColor()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                            modifier = Modifier.statusBarsPadding(),
                            title = {
                                Column {
                                    Text(
                                        "Đăng nhập TikTok",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        subtitleState,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { finish() },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Quay lại"
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { webView?.reload() },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Tải lại")
                                }
                                FilledTonalButton(
                                    onClick = { tryCompleteLogin() },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text("Xong", style = MaterialTheme.typography.labelLargeEmphasized)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                        if (isLoadingState && loadProgressState in 1..99) {
                            LinearWavyProgressIndicator(
                                progress = { loadProgressState / 100f },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        val webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.WHITE)
        }

        // Apply bottom navigation bar + IME insets directly on the native WebView container
        ViewCompat.setOnApplyWindowInsetsListener(webContainer) { view, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(sysBars.bottom, ime.bottom)
            view.setPadding(0, 0, 0, bottom)
            insets
        }

        val wv = createConfiguredWebView()
        webView = wv
        webContainer.addView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(topBarView)
        root.addView(webContainer)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val cur = webView
                if (cur != null && cur.canGoBack()) {
                    cur.goBack()
                } else {
                    finish()
                }
            }
        })

        repo.setLoginUserAgent(AppConstants.DESKTOP_CHROME_UA)
        if (!tryCompleteLogin()) {
            wv.loadUrl(AppConstants.LOGIN_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(): WebView {
        return WebView(this).apply {
            // Standard hardware rendering inside a native ViewGroup (no LAYER_TYPE_SOFTWARE squashing!)
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(Color.WHITE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isForceDarkAllowed = false
            }
            onResume()
            resumeTimers()

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = AppConstants.DESKTOP_CHROME_UA
                useWideViewPort = false
                loadWithOverviewMode = false
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                mediaPlaybackRequiresUserGesture = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = false
                }
            }

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgressState = newProgress
                    isLoadingState = newProgress < 100
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(TAG, "[JS:${it.messageLevel()}] ${it.message()}")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoadingState = true
                    Log.d(TAG, "onPageStarted: $url")
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString().orEmpty()
                    if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                        return false
                    }
                    Log.d(TAG, "Blocked custom scheme: $url")
                    TikTokWebViewHelper.extractFallbackUrlFromCustomScheme(url)
                        ?.let { fallback -> view?.loadUrl(fallback) }
                    return true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        subtitleState = error?.description?.toString() ?: "Lỗi tải trang TikTok"
                        isLoadingState = false
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoadingState = false
                    Log.d(TAG, "onPageFinished: $url")
                    tryCompleteLogin()
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    Log.e(TAG, "Renderer gone (didCrash=${detail?.didCrash()})")
                    subtitleState = "Trang web vừa khởi động lại — bấm Tải lại"
                    return true
                }
            }
        }
    }

    private fun tryCompleteLogin(): Boolean {
        if (completed) return true
        if (!TikTokWebViewHelper.hasValidSessionCookie()) return false

        completed = true
        TikTokWebViewHelper.flushCookies()
        repo.setLoginUserAgent(AppConstants.DESKTOP_CHROME_UA)
        repo.setSessionValid(true)

        var autoEnabled = false
        if (!repo.isScheduled()) {
            StreakScheduler.scheduleNextRun(this)
            autoEnabled = true
        }

        subtitleState = "Đã đăng nhập! Đang lấy tên TikTok..."
        // Extract real TikTok nickName & avatar immediately from the loaded page's rehydration JSON
        val extractJs = """
            (function(){
              try {
                var el = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
                if (el && el.textContent) {
                  var d = JSON.parse(el.textContent);
                  var u = d && d['__DEFAULT_SCOPE__'] && d['__DEFAULT_SCOPE__']['webapp.app-context'] && d['__DEFAULT_SCOPE__']['webapp.app-context'].user;
                  if (u) {
                    var av = (Array.isArray(u.avatarUri) && u.avatarUri.length > 0) ? u.avatarUri[0] : (u.avatarThumb || '');
                    return JSON.stringify({ nick: u.nickName || u.nickname || '', avatar: av || '' });
                  }
                }
              } catch(e) {}
              return '';
            })();
        """.trimIndent()

        val wv = webView
        if (wv != null) {
            wv.evaluateJavascript(extractJs) { rawResult ->
                lifecycleScope.launch {
                    val gotInline = parseAndSaveInlineProfile(rawResult)
                    if (!gotInline) {
                        runCatching { TikTokAccountFetcher.fetchAndStoreDisplayName(this@TikTokLoginActivity) }
                    }
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_AUTO_SCHEDULE_ENABLED, autoEnabled))
                    finish()
                }
            }
        } else {
            lifecycleScope.launch {
                runCatching { TikTokAccountFetcher.fetchAndStoreDisplayName(this@TikTokLoginActivity) }
                setResult(RESULT_OK, Intent().putExtra(EXTRA_AUTO_SCHEDULE_ENABLED, autoEnabled))
                finish()
            }
        }
        return true
    }

    private fun parseAndSaveInlineProfile(rawJsResult: String?): Boolean {
        if (rawJsResult.isNullOrBlank() || rawJsResult == "null" || rawJsResult == "\"\"") return false
        return runCatching {
            // evaluateJavascript wraps string return values in JSON quotes
            val unquoted = org.json.JSONArray("[$rawJsResult]").optString(0)
            if (unquoted.isBlank()) return false
            val obj = org.json.JSONObject(unquoted)
            val nick = obj.optString("nick").takeIf { TikTokAccountFetcher.isValidDisplayName(it) }
            val avatar = obj.optString("avatar").takeIf { it.startsWith("http", ignoreCase = true) }
            if (nick != null || avatar != null) {
                repo.setTikTokAccountProfile(nick, avatar)
                nick != null
            } else false
        }.getOrDefault(false)
    }

    override fun onDestroy() {
        TikTokWebViewHelper.tearDown(webView)
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TikTokLogin"
        const val EXTRA_AUTO_SCHEDULE_ENABLED = "auto_schedule_enabled"
    }
}
