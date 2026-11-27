package com.tnyfishy.sk.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.data.StreakRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScannedChat(
    val username: String,
    val displayName: String,
    val isGroup: Boolean,
    val avatarUrl: String = "",
    val streakDays: Int = 0,
    val streakLitToday: Boolean = false
) {
    val stableKey: String
        get() = if (isGroup) "g:${displayName.lowercase()}" else "u:${username.lowercase()}"
}

data class ChatScanState(
    val isScanning: Boolean = false,
    val statusText: String = "",
    val chats: List<ScannedChat> = emptyList(),
    val error: String? = null,
    val completed: Boolean = false
)

object TikTokChatScanner {
    private const val TAG = "TikTokChatScanner"
    private const val SCAN_TIMEOUT_MS = 240_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var scanInjected = false
    private var timeoutRunnable: Runnable? = null

    private val _scanState = MutableStateFlow(ChatScanState())
    val scanState: StateFlow<ChatScanState> = _scanState.asStateFlow()

    fun startScan(context: Context) {
        val appCtx = context.applicationContext
        val repo = StreakRepository.getInstance(appCtx)

        if (!NetworkConnectivity.hasWifiOrCellularInternet(appCtx)) {
            _scanState.update {
                ChatScanState(
                    isScanning = false,
                    error = "Không có kết nối mạng Wi-Fi hoặc dữ liệu di động.",
                    completed = true
                )
            }
            return
        }

        mainHandler.post {
            cleanupInternal()
            scanInjected = false
            _scanState.update {
                ChatScanState(
                    isScanning = true,
                    statusText = "Đang kết nối hộp thư TikTok...",
                    chats = emptyList(),
                    error = null,
                    completed = false
                )
            }

            try {
                val script = loadMinifiedScanScript(appCtx)
                val ua = repo.getLoginUserAgent()
                val density = appCtx.resources.displayMetrics.density

                val wv = WebView(appCtx)
                webView = wv
                TikTokWebViewHelper.configureForHeadlessAutomation(wv, ua, density)
                wv.addJavascriptInterface(ScannerJsInterface(repo), "ScannerApp")
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val currentUrl = url.orEmpty()
                        when {
                            currentUrl.contains("tiktok.com/messages") -> {
                                if (scanInjected) return
                                scanInjected = true
                                _scanState.update { it.copy(statusText = "Đang quét toàn bộ đoạn chat...") }
                                // Inject almost immediately; script polls DOM every 120ms
                                mainHandler.postDelayed({
                                    webView?.evaluateJavascript(script, null)
                                }, 250)
                            }
                            currentUrl.contains("/login") -> {
                                repo.setSessionValid(false)
                                finishWithError("Phiên TikTok đã hết hạn. Vui lòng đăng nhập lại.")
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val scheme = request?.url?.scheme.orEmpty().lowercase()
                        return scheme != "http" && scheme != "https"
                    }
                }

                wv.loadUrl(AppConstants.MESSAGES_URL)

                val timeout = Runnable {
                    if (_scanState.value.isScanning) {
                        val count = _scanState.value.chats.size
                        cleanupInternal()
                        _scanState.update {
                            it.copy(
                                isScanning = false,
                                completed = true,
                                statusText = if (count > 0) "Đã quét được $count cuộc trò chuyện" else "",
                                error = if (count == 0) "Hết thời gian chờ khi tải hộp thư TikTok." else null
                            )
                        }
                    }
                }
                timeoutRunnable = timeout
                mainHandler.postDelayed(timeout, SCAN_TIMEOUT_MS)
            } catch (ex: Exception) {
                finishWithError("Không thể khởi tạo trình quét: ${ex.message}")
            }
        }
    }

    fun cancelScan() {
        mainHandler.post {
            cleanupInternal()
            _scanState.update {
                it.copy(
                    isScanning = false,
                    completed = true
                )
            }
        }
    }

    fun resetState() {
        cancelScan()
        _scanState.update { ChatScanState() }
    }

    private fun finishWithError(msg: String) {
        cleanupInternal()
        _scanState.update {
            it.copy(
                isScanning = false,
                error = msg,
                completed = true
            )
        }
    }

    private fun cleanupInternal() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        TikTokWebViewHelper.tearDown(webView)
        webView = null
        scanInjected = false
    }

    private fun loadMinifiedScanScript(context: Context): String {
        val raw = context.assets.open("scan_chats.js").bufferedReader().use { it.readText() }
        return raw.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    class ScannerJsInterface(private val repo: StreakRepository) {
        @JavascriptInterface
        fun onAccountProfile(nickname: String, avatarUrl: String) {
            mainHandler.post {
                repo.setTikTokAccountProfile(
                    nickname = nickname.trim().ifBlank { null },
                    avatarUrl = avatarUrl.trim().ifBlank { null }
                )
            }
        }

        @JavascriptInterface
        fun onProgress(count: Int, status: String) {
            mainHandler.post {
                _scanState.update { cur ->
                    if (!cur.isScanning) cur else cur.copy(statusText = status)
                }
            }
        }

        @JavascriptInterface
        fun onChatFound(
            username: String,
            displayName: String,
            isGroup: Boolean,
            avatarUrl: String,
            streakDays: Int,
            streakLitToday: Boolean
        ) {
            mainHandler.post {
                if (!_scanState.value.isScanning) return@post
                val entry = ScannedChat(
                    username = username.trim().removePrefix("@"),
                    displayName = displayName.trim().ifBlank { username.trim() },
                    isGroup = isGroup,
                    avatarUrl = avatarUrl.trim(),
                    streakDays = streakDays.coerceAtLeast(0),
                    streakLitToday = streakLitToday
                )
                repo.syncMetadataFromScan(entry)
                _scanState.update { cur ->
                    if (!cur.isScanning) cur
                    else if (cur.chats.none { it.stableKey == entry.stableKey }) {
                        cur.copy(chats = cur.chats + entry)
                    } else cur
                }
            }
        }

        @JavascriptInterface
        fun onScanComplete(total: Int, error: String) {
            mainHandler.post {
                cleanupInternal()
                _scanState.update { cur ->
                    cur.copy(
                        isScanning = false,
                        completed = true,
                        statusText = "Hoàn tất (${cur.chats.size} cuộc trò chuyện)",
                        error = error.takeIf { it.isNotBlank() && cur.chats.isEmpty() }
                    )
                }
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }
}
