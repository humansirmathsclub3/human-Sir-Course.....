package com.humansir.myapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.CookieManager;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.graphics.Bitmap;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private LinearLayout loadingLayout;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String LAST_CACHE_CLEAR_KEY = "last_cache_clear";
    private static final long ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private boolean isModifyingClipboard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Prevent screen recording (Note: In Android, FLAG_SECURE prevents both screen recording and screenshots)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        // Hide status bar and navigation bar for full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();
        
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        loadingLayout = findViewById(R.id.loading_layout);
        
        // Disable long clicks to prevent text selection and context menus
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        webView.setLongClickable(false);

        // Obfuscate any text copied to the clipboard while the app is running
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipListener = new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    if (isModifyingClipboard) return;
                    if (clipboard.hasPrimaryClip()) {
                        ClipData clip = clipboard.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence text = clip.getItemAt(0).getText();
                            if (text != null) {
                                isModifyingClipboard = true;
                                String obfuscated = obfuscateString(text.toString());
                                ClipData newClip = ClipData.newPlainText("obfuscated", obfuscated);
                                clipboard.setPrimaryClip(newClip);
                                isModifyingClipboard = false;
                            }
                        }
                    }
                }
            };
            clipboard.addPrimaryClipChangedListener(clipListener);
        }
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);
        
        // Fix for Google OAuth "disallowed_useragent" error
        String userAgent = webSettings.getUserAgentString();
        webSettings.setUserAgentString(userAgent.replace("; wv", ""));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Handle Cache Clearing (1 week)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastClear = prefs.getLong(LAST_CACHE_CLEAR_KEY, 0);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClear > ONE_WEEK_MS) {
            webView.clearCache(true);
            prefs.edit().putLong(LAST_CACHE_CLEAR_KEY, currentTime).apply();
        }

        // Enable Cookies (including third-party for Google Login)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                loadingLayout.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                injectProtectionJS(view);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                injectProtectionJS(view);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                hideSystemUI();
            }

            @Override
            public void onHideCustomView() {
                hideFullScreen();
            }
        });
        
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("https://script.google.com/macros/s/AKfycbyjeN_3GMou0x45M1nC_d5IALv1kc8V_W7tZtF57OllKuJoWfiCfaBrIDHzszvbCdMd8w/exec");
        }

        // Privacy Policy URL is kept in the APK code as requested, but hidden from UI
        String privacyUrl = "https://policies.google.com/privacy";
    }
    
    private void hideFullScreen() {
        if (customView == null) {
            return;
        }
        webView.setVisibility(View.VISIBLE);
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenContainer.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        hideSystemUI();
    }
    
    private void injectProtectionJS(WebView view) {
        String js = "javascript:(function() {" +
            "var style = document.createElement('style');" +
            "style.innerHTML = '* { -webkit-touch-callout: none !important; -webkit-user-select: none !important; user-select: none !important; }';" +
            "document.head.appendChild(style);" +
            "window.addEventListener('contextmenu', function(e) { e.preventDefault(); });" +
            "function obfuscate(t) {" +
            "  if(!t) return t;" +
            "  return t.split('').map(function(c) {" +
            "    if(/[a-z]/.test(c)) return String.fromCharCode((c.charCodeAt(0)-97+10)%26+97).toUpperCase();" +
            "    if(/[A-Z]/.test(c)) return String.fromCharCode((c.charCodeAt(0)-65+10)%26+65).toLowerCase();" +
            "    if(/[0-9]/.test(c)) return String.fromCharCode((c.charCodeAt(0)-48+5)%10+48);" +
            "    return c;" +
            "  }).join('');" +
            "}" +
            "document.addEventListener('copy', function(e) {" +
            "  var text = window.getSelection().toString();" +
            "  if(text) {" +
            "    e.clipboardData.setData('text/plain', obfuscate(text));" +
            "    e.preventDefault();" +
            "  }" +
            "});" +
            "if(navigator.clipboard && navigator.clipboard.writeText) {" +
            "  var orig = navigator.clipboard.writeText;" +
            "  navigator.clipboard.writeText = function(t) { return orig.call(navigator.clipboard, obfuscate(t)); };" +
            "}" +
            "})()";
        view.evaluateJavascript(js, null);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideFullScreen();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private String obfuscateString(String t) {
        if (t == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                sb.append(Character.toUpperCase((char) (((c - 'a' + 10) % 26) + 'a')));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(Character.toLowerCase((char) (((c - 'A' + 10) % 26) + 'A')));
            } else if (c >= '0' && c <= '9') {
                sb.append((char) (((c - '0' + 5) % 10) + '0'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
