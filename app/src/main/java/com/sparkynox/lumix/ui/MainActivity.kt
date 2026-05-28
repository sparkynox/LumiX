package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.sparkynox.lumix.databinding.ActivityMainBinding
import com.sparkynox.lumix.service.PlayerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var webView: WebView? = null

    // Full ad-blocking + muting JS
    private val adBlockJS = """
        (function() {
            // --- SKIP BUTTON AUTO CLICK ---
            function skipAd() {
                var skipBtn = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, button.ytp-ad-skip-button-modern');
                if (skipBtn) { skipBtn.click(); }
            }

            // --- HIDE & MUTE ADS ---
            function nukeAds() {
                // Hide ad containers
                var adSelectors = [
                    '.ytp-ad-module',
                    '.ytp-ad-overlay-container',
                    '.ytp-ad-text-overlay',
                    '.ytp-ad-image-overlay',
                    '.ytp-ad-player-overlay',
                    '.ytp-ad-action-interstitial',
                    '#masthead-ad',
                    '#player-ads',
                    'ytd-banner-promo-renderer',
                    'ytd-video-masthead-ad-v3-renderer',
                    'ytd-in-feed-ad-layout-renderer',
                    'ytd-ad-slot-renderer',
                    'ytd-statement-banner-renderer',
                    'ytd-display-ad-renderer',
                    'ytd-promoted-sparkles-web-renderer',
                    'ytd-promoted-video-renderer',
                    'tp-yt-paper-dialog',
                    '.ytd-merch-shelf-renderer',
                    '#player-ads',
                    '.ad-showing .ytp-ad-player-overlay'
                ];
                adSelectors.forEach(function(sel) {
                    var els = document.querySelectorAll(sel);
                    els.forEach(function(el) {
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('opacity', '0', 'important');
                        el.style.setProperty('height', '0', 'important');
                        el.style.setProperty('width', '0', 'important');
                        el.style.setProperty('position', 'absolute', 'important');
                    });
                });

                // Mute ad video
                var video = document.querySelector('video');
                if (video) {
                    var isAd = document.querySelector('.ad-showing');
                    if (isAd) {
                        video.muted = true;
                        video.volume = 0;
                        // Try to skip by jumping to end
                        if (video.duration && !isNaN(video.duration) && video.duration > 0) {
                            try { video.currentTime = video.duration; } catch(e) {}
                        }
                    } else {
                        // Restore sound for real video
                        if (video.muted) { video.muted = false; video.volume = 1; }
                    }
                }
            }

            // --- REMOVE AD OVERLAYS ON VIDEO ---
            function removeOverlays() {
                var overlays = document.querySelectorAll('.ytp-ce-element, .ytp-cards-teaser, .ytp-suggested-action');
                overlays.forEach(function(el) {
                    el.style.setProperty('display', 'none', 'important');
                });
            }

            // Run every 300ms
            setInterval(function() {
                skipAd();
                nukeAds();
                removeOverlays();
            }, 300);

            // Also run on DOM changes
            var observer = new MutationObserver(function() {
                skipAd();
                nukeAds();
            });
            observer.observe(document.body, { childList: true, subtree: true });

        })();
    """.trimIndent()

    // Background play JS — prevents YouTube from pausing when tab hidden
    private val backgroundPlayJS = """
        (function() {
            // Override visibility API so YouTube thinks page is always visible
            Object.defineProperty(document, 'hidden', { get: function() { return false; } });
            Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
            document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
            document.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);

            // Prevent YouTube pause on blur
            window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
            window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);

            console.log('Lumi X: Background play enabled');
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupButtons()

        // Start foreground service immediately for background play
        startForegroundService()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = binding.webView
        webView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject JS on every page load
                    view?.evaluateJavascript(backgroundPlayJS, null)
                    view?.evaluateJavascript(adBlockJS, null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // Stay inside WebView — don't open external browser
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    // Inject early on 50% load too
                    if (newProgress >= 50) {
                        view?.evaluateJavascript(adBlockJS, null)
                    }
                }
            }

            // Load YouTube
            loadUrl("https://m.youtube.com")
        }
    }

    private fun setupButtons() {
        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_START
        }
        startService(intent)
    }

    // Handle back button inside WebView
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        // Don't pause WebView — let it keep playing
        webView?.onResume()
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}
