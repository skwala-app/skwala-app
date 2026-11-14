# keep the JavaScript bridge used by the SK WALA website
-keepclassmembers class com.skwala.app.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
