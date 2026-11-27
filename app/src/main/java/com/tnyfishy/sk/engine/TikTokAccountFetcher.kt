package com.tnyfishy.sk.engine

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.data.StreakRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TikTokAccountProfile(
    val nickname: String?,
    val avatarUrl: String?
)

object TikTokAccountFetcher {
    private const val TAG = "TikTokAccountFetcher"

    /**
     * Fetches the logged-in user's real TikTok Display Name (nickName, NEVER @username / screen_name)
     * and profile avatar URL using the WebView's stored session cookies over HTTPS.
     */
    suspend fun fetchAndStoreDisplayName(context: Context): String? = withContext(Dispatchers.IO) {
        val repo = StreakRepository.getInstance(context)
        val cookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.tiktok.com")
        }.getOrNull().orEmpty()

        if (!cookies.contains("sessionid=")) return@withContext null
        val ua = repo.getLoginUserAgent()

        // 1) Primary: __UNIVERSAL_DATA_FOR_REHYDRATION__ on /messages or /foryou
        //    (Contains the TRUE display name `nickName` + `avatarUri`, NOT the handle!)
        val p1 = fetchFromRehydrationPage(AppConstants.MESSAGES_URL, cookies, ua)
            ?: fetchFromRehydrationPage("https://www.tiktok.com/foryou?lang=vi-VN", cookies, ua)

        var nickname = p1?.nickname
        var avatar = p1?.avatarUrl

        // 2) Secondary: passport account info — ONLY read nickname/avatar_url (NEVER screen_name which is "user123..." handle)
        if (nickname.isNullOrBlank() || avatar.isNullOrBlank()) {
            val p2 = fetchFromPassportInfo(cookies, ua)
            if (nickname.isNullOrBlank()) nickname = p2?.nickname
            if (avatar.isNullOrBlank()) avatar = p2?.avatarUrl
        }

        if (!nickname.isNullOrBlank() || !avatar.isNullOrBlank()) {
            Log.d(TAG, "Synced TikTok profile: nickname=$nickname, hasAvatar=${!avatar.isNullOrBlank()}")
            repo.setTikTokAccountProfile(nickname, avatar)
        }
        nickname
    }

    private fun fetchFromRehydrationPage(url: String, cookies: String, ua: String): TikTokAccountProfile? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                instanceFollowRedirects = true
                setRequestProperty("Cookie", cookies)
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val marker = "id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\""
                val startIdx = html.indexOf(marker)
                if (startIdx < 0) return@runCatching null
                val jsonStart = html.indexOf('>', startIdx)
                val jsonEnd = html.indexOf("</script>", jsonStart)
                if (jsonStart < 0 || jsonEnd <= jsonStart) return@runCatching null

                val root = JSONObject(html.substring(jsonStart + 1, jsonEnd))
                val user = root.optJSONObject("__DEFAULT_SCOPE__")
                    ?.optJSONObject("webapp.app-context")
                    ?.optJSONObject("user")
                    ?: return@runCatching null

                val nick = listOf(
                    user.optString("nickName"),
                    user.optString("nickname")
                ).firstOrNull { isValidDisplayName(it) }?.trim()

                var avatar: String? = null
                val uriArr = user.optJSONArray("avatarUri")
                if (uriArr != null && uriArr.length() > 0) {
                    avatar = uriArr.optString(0).takeIf { it.startsWith("http", true) }
                }
                if (avatar.isNullOrBlank()) {
                    avatar = listOf(
                        user.optString("avatarThumb"),
                        user.optString("avatarMedium"),
                        user.optString("avatarLarger")
                    ).firstOrNull { it.startsWith("http", true) }
                }

                if (nick != null || avatar != null) TikTokAccountProfile(nick, avatar) else null
            } finally {
                conn.disconnect()
            }
        }.onFailure {
            Log.d(TAG, "rehydration fetch ($url) failed: ${it.message}")
        }.getOrNull()
    }

    private fun fetchFromPassportInfo(cookies: String, ua: String): TikTokAccountProfile? {
        return runCatching {
            val conn = (URL("https://www.tiktok.com/passport/web/account/info/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Cookie", cookies)
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Referer", "https://www.tiktok.com/")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body).optJSONObject("data") ?: return@runCatching null
                // Intentionally skip screen_name & username (those are @handles like "user123456...")
                val nick = listOf(
                    data.optString("nickname"),
                    data.optString("nick_name")
                ).firstOrNull { isValidDisplayName(it) }?.trim()
                val avatar = listOf(
                    data.optString("avatar_url"),
                    data.optString("avatar_thumb"),
                    data.optString("avatar")
                ).firstOrNull { it.startsWith("http", ignoreCase = true) }?.trim()
                if (nick != null || avatar != null) TikTokAccountProfile(nick, avatar) else null
            } finally {
                conn.disconnect()
            }
        }.onFailure {
            Log.d(TAG, "passport account info failed: ${it.message}")
        }.getOrNull()
    }

    fun isValidDisplayName(raw: String?): Boolean {
        val s = raw?.trim().orEmpty()
        if (s.isBlank() || s.equals("null", ignoreCase = true) || s.equals("User", ignoreCase = true)) {
            return false
        }
        // Reject raw auto-generated TikTok handle strings like "user704433227667"
        if (Regex("^user\\d{6,}$", RegexOption.IGNORE_CASE).matches(s)) {
            return false
        }
        return true
    }
}
