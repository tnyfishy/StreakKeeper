# Preserve only WebView @JavascriptInterface annotated bridge methods
-keepattributes *Annotation*
-keepclassmembers class com.tnyfishy.sk.service.StreakService$StreakJsInterface {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.tnyfishy.sk.engine.TikTokChatScanner$ScannerJsInterface {
    @android.webkit.JavascriptInterface <methods>;
}
