package com.skwala.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * SK WALA – Android app for the SK WALA mock-test website (Google Apps Script web app).
 *  - professional start screen, bottom menu (Home / Results / Messages / More)
 *  - notifications for new mock tests, announcements and messages
 *  - screenshots and screen recording blocked
 *  - UPI apps for payments, PDF viewer for shared files, offline screen
 */
public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 4201;
    private static final int NOTIFY_PERMISSION_REQUEST = 4202;
    private static final int BLUE = 0xFF1F4E8C, ORANGE = 0xFFE5640F, GREY = 0xFF7A8796;

    private WebView web;
    private View offline, loader, nav, navLine;
    private ProgressBar bar;
    private ValueCallback<Uri[]> fileCallback;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** what the SK WALA page reports: login / home / exam */
    private volatile String screen = "login";
    /** a tap on the bottom menu, picked up by the page */
    private volatile String pendingAction = "";
    private String selectedTab = "home";
    private final LinearLayout[] tabs = new LinearLayout[4];
    private static final String[] TAB_KEYS = {"home", "results", "messages", "more"};

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // No screenshots / screen recording inside the app (protects papers and solutions)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();

        web = findViewById(R.id.web);
        offline = findViewById(R.id.offline);
        loader = findViewById(R.id.loader);
        bar = findViewById(R.id.bar);
        nav = findViewById(R.id.nav);
        navLine = findViewById(R.id.navline);
        findViewById(R.id.retry).setOnClickListener(v -> load());
        buildBottomMenu();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // keeps the student logged in
        s.setSupportMultipleWindows(true);         // links that open "in a new tab"
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " SKWALAApp/" + BuildConfigCompat.versionName(this));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "SKApp");
        web.setWebViewClient(new SiteClient());
        web.setWebChromeClient(new SiteChrome());
        web.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            if (url != null && url.startsWith("http")) openExternal(Uri.parse(url));
        });

        setupBack();
        handleOpenExtra(getIntent());

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
            loader.setVisibility(View.GONE);
        } else {
            load();
        }
        Notifier.createChannel(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleOpenExtra(intent);
    }

    /** a tap on a notification ("New message …") opens the right page */
    private void handleOpenExtra(Intent intent) {
        if (intent == null) return;
        String open = intent.getStringExtra(Notifier.EXTRA_OPEN);
        if (open != null && !open.isEmpty()) {
            pendingAction = open;
            selectTab(open);
        }
    }

    private void load() {
        offline.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        String current = web.getUrl();
        if (current != null && current.startsWith("http")) web.reload();
        else web.loadUrl(getString(R.string.site_url));
    }

    /* ---------------- screen layout ---------------- */

    /** draws behind the status / navigation bars on every Android version and pads the content correctly */
    private void setupEdgeToEdge() {
        View root = findViewById(R.id.root);
        View content = findViewById(R.id.content);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            root.setSystemUiVisibility(flags);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                l = bars.left; t = bars.top; r = bars.right; b = Math.max(bars.bottom, ime.bottom);
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, 0);          // blue behind the status bar
            content.setPadding(0, 0, 0, b);    // white behind the navigation bar
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildBottomMenu() {
        LinearLayout box = (LinearLayout) nav;
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_results, R.drawable.ic_nav_messages, R.drawable.ic_nav_more};
        int[] labels = {R.string.nav_home, R.string.nav_results, R.string.nav_messages, R.string.nav_more};
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        for (int i = 0; i < 4; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackgroundResource(ripple.resourceId);
            item.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView text = new TextView(this);
            text.setText(labels[i]);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            text.setGravity(Gravity.CENTER);
            text.setPadding(0, dp(3), 0, 0);
            item.addView(icon);
            item.addView(text);
            final String key = TAB_KEYS[i];
            item.setOnClickListener(v -> onTab(key));
            box.addView(item);
            tabs[i] = item;
        }
        selectTab("home");
    }

    private void selectTab(String key) {
        if (!"more".equals(key)) selectedTab = key;
        for (int i = 0; i < 4; i++) {
            boolean on = TAB_KEYS[i].equals(selectedTab);
            int color = on ? ORANGE : GREY;
            ImageView icon = (ImageView) tabs[i].getChildAt(0);
            TextView text = (TextView) tabs[i].getChildAt(1);
            icon.setImageTintList(ColorStateList.valueOf(color));
            text.setTextColor(on ? BLUE : GREY);
            text.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void onTab(String key) {
        if ("more".equals(key)) { showMoreMenu(); return; }
        selectTab(key);
        pendingAction = key;   // the page shows Home / Results / Messages
    }

    /** bottom menu only on the student pages – hidden on the login page and during a test */
    private void applyScreen() {
        boolean show = "home".equals(screen);
        nav.setVisibility(show ? View.VISIBLE : View.GONE);
        navLine.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showMoreMenu() {
        final String email = getString(R.string.contact_email).trim();
        final java.util.List<String> items = new java.util.ArrayList<>();
        items.add("📤  Share SK WALA app");
        items.add("⭐  Rate SK WALA on Google Play");
        items.add("🔔  Notification settings");
        if (!email.isEmpty()) items.add("✉️  Contact Shreenath Kulal");
        items.add("🔒  Privacy policy");
        items.add("ℹ️  About SK WALA");
        items.add("↻  Reload");
        new AlertDialog.Builder(this)
                .setTitle("SK WALA")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String it = items.get(which);
                    if (it.contains("Share")) shareApp();
                    else if (it.contains("Rate")) openExternal(Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()));
                    else if (it.contains("Notification")) openNotificationSettings();
                    else if (it.contains("Contact")) openSpecial("mailto:" + email + "?subject=" + Uri.encode("SK WALA app"));
                    else if (it.contains("Privacy")) openExternal(Uri.parse(getString(R.string.privacy_url)));
                    else if (it.contains("About")) showAbout();
                    else if (it.contains("Reload")) web.reload();
                })
                .show();
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "SK WALA – JEE Main & KCET Mock Tests");
        i.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text));
        try { startActivity(Intent.createChooser(i, "Share SK WALA")); } catch (ActivityNotFoundException ignored) { }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle("SK WALA  v" + BuildConfigCompat.versionName(this))
                .setMessage(R.string.about_text)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openNotificationSettings() {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 26) {
                i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            }
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings → Apps → SK WALA → Notifications", Toast.LENGTH_LONG).show();
        }
    }

    /* ---------------- back button (Android 16 predictive back + older phones) ---------------- */

    private void setupBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {   // Android 12 and older
        handleBack();
    }

    private void handleBack() {
        if (web.canGoBack()) { web.goBack(); return; }
        if ("home".equals(screen) && !"home".equals(selectedTab)) { onTab("home"); return; }
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_text)
                .setPositiveButton(R.string.exit_yes, (d, w) -> finish())
                .setNegativeButton(R.string.exit_no, null)
                .show();
    }

    /* ---------------- links ---------------- */

    /** the website itself (Apps Script) stays inside the app; everything else opens outside */
    private static boolean isSiteHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("script.google.com")
                || host.endsWith(".googleusercontent.com")
                || host.equals("accounts.google.com");
    }

    /** upi://, intent://, tel:, mailto: … → the right app on the phone */
    private void openSpecial(String url) {
        try {
            Intent i;
            if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            if (url.startsWith("upi:")) i = Intent.createChooser(i, "Pay with");
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, url.startsWith("upi:")
                    ? "No UPI app found. Please install GPay, PhonePe, Paytm or BHIM, or scan the QR code."
                    : "No app found to open this link.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser found to open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    /** a link that wanted a new tab / window */
    private void route(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) { openSpecial(uri.toString()); return; }
        if (isSiteHost(uri.getHost())) web.loadUrl(uri.toString());
        else openExternal(uri);   // e.g. Razorpay payment page, Google Drive files, other websites
    }

    private class SiteClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                if (scheme.equals("about") || scheme.equals("blob") || scheme.equals("data") || scheme.equals("javascript")) return false;
                openSpecial(uri.toString());
                return true;
            }
            if (!request.isForMainFrame()) return false;
            if (isSiteHost(uri.getHost())) return false;
            openExternal(uri);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            bar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            bar.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
            // the SK WALA page needs a moment to draw inside Apps Script
            if (loader.getVisibility() == View.VISIBLE) ui.postDelayed(() -> loader.setVisibility(View.GONE), 900);
            // hide Google's grey "created by a Google Apps Script user" bar inside the app (tap ✕ does the same on the website)
            hideScriptBar();
            ui.postDelayed(MainActivity.this::hideScriptBar, 1500);
            ui.postDelayed(MainActivity.this::hideScriptBar, 4000);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                loader.setVisibility(View.GONE);
                web.setVisibility(View.INVISIBLE);
                offline.setVisibility(View.VISIBLE);
            }
        }
    }

    private static final String HIDE_BAR_JS = "(function(){try{var els=document.querySelectorAll('body *');"
            + "for(var i=0;i<els.length;i++){var e=els[i];if(e.tagName==='IFRAME')continue;"
            + "var t=e.textContent||'';if(t.length<300&&/created by a Google Apps Script user/i.test(t)&&e.offsetHeight>0&&e.offsetHeight<120){"
            + "var p=e;while(p.parentElement&&p.parentElement!==document.body&&p.parentElement.offsetHeight<120&&!p.parentElement.querySelector('iframe'))p=p.parentElement;"
            + "p.style.display='none';}}}catch(x){}})();";

    private void hideScriptBar() {
        try { web.evaluateJavascript(HIDE_BAR_JS, null); } catch (Exception ignored) { }
    }

    private class SiteChrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            bar.setProgress(progress);
            bar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
        }

        /** "open in new tab" links and payment windows */
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                private boolean done = false;

                private boolean take(String url) {
                    if (done || url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) return false;
                    done = true;
                    route(Uri.parse(url));
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                    take(r.getUrl().toString());
                    return true;
                }

                @Override
                public void onPageStarted(WebView w, String url, Bitmap favicon) {
                    if (take(url)) w.stopLoading();
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }

        /** file upload buttons (admin uploads Word papers / PDFs from the phone) */
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), FILE_REQUEST);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "No file manager found.", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    /* ---------------- bridge used by the SK WALA page (window.SKApp) ---------------- */

    public class Bridge {
        @JavascriptInterface
        public boolean isApp() { return true; }

        /** true when this copy was installed from Google Play – the page then hides in-app payments (Play Payments policy: consumption-only) */
        @JavascriptInterface
        public boolean fromPlay() { return installedFromPlay(); }

        /** login / home / exam – controls the bottom menu */
        @JavascriptInterface
        public void setScreen(String s) {
            screen = s == null ? "login" : s;
            ui.post(MainActivity.this::applyScreen);
        }

        /** the logged-in student (used only for notifications about their own messages) */
        @JavascriptInterface
        public void setUser(String sid, String token, String name) {
            ui.post(() -> onUser(sid, token));
        }

        /** the page asks every moment: did the student tap the bottom menu? */
        @JavascriptInterface
        public String takeAction() {
            String a = pendingAction;
            pendingAction = "";
            return a == null ? "" : a;
        }

        @JavascriptInterface
        public void openFile(String name, String mime, String b64) { handleFile(name, mime, b64, false); }

        @JavascriptInterface
        public void saveFile(String name, String mime, String b64) { handleFile(name, mime, b64, true); }
    }

    @SuppressWarnings("deprecation")
    private boolean installedFromPlay() {
        try {
            String inst;
            if (Build.VERSION.SDK_INT >= 30) inst = getPackageManager().getInstallSourceInfo(getPackageName()).getInstallingPackageName();
            else inst = getPackageManager().getInstallerPackageName(getPackageName());
            return "com.android.vending".equals(inst) || "com.google.android.feedback".equals(inst);
        } catch (Exception e) {
            return false;
        }
    }

    private void onUser(String sid, String token) {
        boolean loggedIn = sid != null && !sid.isEmpty() && token != null && !token.isEmpty();
        boolean changed = Notifier.saveUser(this, loggedIn ? sid : "", loggedIn ? token : "");
        if (!loggedIn) { if (changed) NotifyWorker.cancel(this); return; }
        if (changed) NotifyWorker.schedule(this);
        askNotificationPermissionOnce();
    }

    private void askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        if (Notifier.prefs(this).getBoolean("askedNotify", false)) return;
        Notifier.prefs(this).edit().putBoolean("askedNotify", true).apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY_PERMISSION_REQUEST);
    }

    private static String safeName(String name) {
        String n = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        return n.isEmpty() ? "file" : n;
    }

    private void handleFile(String name, String mime, String b64, boolean save) {
        final String fname = safeName(name);
        final String type = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                if (save && Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, type);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SK WALA");
                    ContentResolver cr = getContentResolver();
                    Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("no space");
                    try (OutputStream out = cr.openOutputStream(uri)) {
                        if (out == null) throw new Exception("cannot write");
                        out.write(bytes);
                    }
                    ui.post(() -> Toast.makeText(this, "Saved in Downloads/SK WALA: " + fname, Toast.LENGTH_LONG).show());
                    return;
                }
                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("cannot create folder");
                File f = new File(dir, fname);
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
                Intent i;
                if (save) {
                    i = new Intent(Intent.ACTION_SEND);
                    i.setType(type);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i = Intent.createChooser(i, "Save or share " + fname);
                } else {
                    i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, type);
                }
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                final Intent intent = i;
                ui.post(() -> {
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, "No app found to open this file. Please install a PDF viewer (e.g. Google Drive).", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "Could not open the file.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
