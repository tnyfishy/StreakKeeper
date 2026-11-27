package com.tnyfishy.sk

object AppConstants {
    const val ACTION_STREAK_ALARM = "com.tnyfishy.sk.ACTION_STREAK_ALARM"
    const val ACTION_STOP_SERVICE = "com.tnyfishy.sk.ACTION_STOP_SERVICE"
    const val EXTRA_TARGET_FRIEND_IDS = "target_friend_ids"

    const val LOGIN_URL = "https://www.tiktok.com/login"
    const val MESSAGES_URL = "https://www.tiktok.com/messages?lang=en"

    const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val DEFAULT_MESSAGE = "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25"
    const val LEGACY_DEFAULT_MESSAGE = "Hey! Keeping our streak alive! \uD83D\uDD25"
    const val ALREADY_STREAK_TODAY_TOKEN = "ALREADY_STREAK_TODAY"
    const val DEFAULT_INTERVAL_HOURS = 23
    const val MAX_RETRIES_PER_DAY = 3

    const val FAILURE_REASON_NO_NETWORK = "no_network"
    const val FAILURE_REASON_SEND_ERROR = "send_error"
    const val USER_NOT_FOUND_ERROR = "User not found in chat list"

    val BUILT_IN_STREAK_MESSAGES: List<String> = listOf(
        "Streak", "streak", "streakk", "streaak", "streaakkk",
        "streaaak", "strk", "Strk", "s", "S",
        "streaks", "Streaks", "streakss", "streak lol", "yo streak",
        "yoo streak", "yoo streakk", "hey streak", "hii streak", "hi streak",
        "heyy streak", "streak hii", "streak hi", "streak yo", "streak yoo",
        "streakkk", "strek", "streek", "streeek", "streeeek",
        "yo", "yoo", "yooo", "hey", "hii",
        "heyy", "heyyy", "here", "heree", "strkeee",
        "streak rn", "quick streak", "streakk lol", "streak lmao", "lol streak",
        "streaaakk", "streakkk lol", "daily streak", "streak streak", "ayo streak"
    )
}
