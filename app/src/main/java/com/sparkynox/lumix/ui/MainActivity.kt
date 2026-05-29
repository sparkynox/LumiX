package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.sparkynox.lumix.databinding.ActivityMainBinding
import com.sparkynox.lumix.service.PlayerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var wakeLock: PowerManager.WakeLock? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val backgroundJS = """
        (function() {
            if (window._lumiXBgDone) return;
            window._lumiXBgDone = true;

            // Always visible
            try {
                Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
            } catch(e) {}

            const block = (e) => { e.stopImmediatePropagation(); e.preventDefault(); };
            document.addEventListener('visibilitychange', block, true);
            document.addEventListener('webkitvisibilitychange', block, true);
            window.addEventListener('blur', block, true);
            window.addEventListener('pagehide', block, true);
            window.addEventListener('freeze', block, true);

            // Keep video alive every 300ms
            setInterval(() => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing');
                if (video && !isAd && video.paused && video.currentTime > 0 && !video.ended) {
                    video.play().catch(() => {});
                }
            }, 300);

            // Prevent YouTube from pausing on visibility change
            document.addEventListener('pause', (e) => {
                const video = e.target;
                const isAd = document.querySelector('.ad-showing');
                if (video && video.tagName === 'VIDEO' && !isAd) {
                    setTimeout(() => { video.play().catch(() => {}); }, 100);
                }
            }, true);
        })();
    """.trimIndent()

    private val adBlockJS = """
        (function() {
            if (window._lumiXAdDone) return;
            window._lumiXAdDone = true;

            const style = document.createElement('style');
            style.textContent = `
                .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                .ytd-display-ad-renderer, .ytp-ad-overlay-container,
                ytd-ad-slot-renderer, #masthead-ad, .ytp-ad-progress-list,
                .ytp-ad-action-interstitial, .ytp-ad-skip-button-container,
                .ytp-ad-feedback-dialog-container, ytd-banner-promo-renderer,
                ytd-statement-banner-renderer, ytd-in-feed-ad-layout-renderer,
                .ytp-ad-visit-advertiser-button, .ytp-ad-button,
                .ytp-ad-duration-remaining, .ytp-ad-simple-ad-badge {
                    display: none !important;
                    visibility: hidden !important;
                    opacity: 0 !important;
                    height: 0 !important;
                    width: 0 !important;
                    pointer-events: none !important;
                }
                .ad-showing video {
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
                .ad-showing .html5-video-container {
                    background: #000 !important;
                }
            `;
            if (document.head) document.head.appendChild(style);

            function handleAd() {
                const isAd = !!document.querySelector('.ad-showing');
                const video = document.querySelector('video');
                if (!video) return;

                if (isAd) {
                    // Mute + hide
                    video.muted = true;
                    video.volume = 0;
                    video.style.setProperty('opacity', '0', 'important');
                    video.style.setProperty('pointer-events', 'none', 'important');

                    // Max speed — keep hammering it
                    try {
                        if (video.playbackRate < 16) video.playbackRate = 16;
                    } catch(e) {}

                    // Force playbackRate via property override
                    try {
                        Object.defineProperty(video, 'playbackRate', {
                            get: () => 16,
                            set: () => {},
                            configurable: true
                        });
                    } catch(e) {}

                    // Click skip
                    document.querySelectorAll(
                        '.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, button[class*="skip"]'
                    ).forEach(btn => { try { btn.click(); } catch(e) {} });

                    // Hide ad containers
                    document.querySelectorAll(
                        '.ytp-ad-player-overlay, .ytp-ad-module, .video-ads, .ytp-ad-overlay-container'
                    ).forEach(el => el.style.setProperty('display', 'none', 'important'));

                } else {
                    // Restore for real video
                    try {
                        Object.defineProperty(video, 'playbackRate', {
                            get: () => video._realRate || 1,
                            set: (v) => { video._realRate = v; },
                            configurable: true
                        });
                    } catch(e) {}
                    video.muted = false;
                    video.volume = 1;
                    video.style.removeProperty('opacity');
                    video.style.removeProperty('pointer-events');
                    try {
                        if (video.playbackRate !== 1) video.playbackRate = 1;
                    } catch(e) {}
                }
            }

            // Hammer every 100ms via setInterval — more reliable than rAF when screen off
            setInterval(() => { handleAd(); }, 100);

            // Also rAF for when screen on
            let last = 0;
            function loop(t) {
                if (t - last > 100) { handleAd(); last = t; }
                requestAnimationFrame(loop);
            }
            requestAnimationFrame(loop);

            new MutationObserver(() => handleAd())
                .observe(document.body, { childList: true, subtree: true });

            document.addEventListener('play', handleAd, true);
            document.addEventListener('playing', handleAd, true);
            document.addEventListener('ratechange', () => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing');
                if (video && isAd && video.playbackRate < 16) {
                    try { video.playbackRate = 16; } catch(e) {}
                }
            }, true);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        acquireWakeLock()
        setupWebView()
        setupButtons()
        startForegroundService()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LumiX::BackgroundPlay")
        wakeLock?.acquire(12 * 60 * 60 * 1000L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowContentAccess = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAll(view)
                    // Re-inject after 1s, 3s, 5s — YouTube lazy loads
                    view?.postDelayed({ injectAll(view) }, 1000)
                    view?.postDelayed({ injectAll(view) }, 3000)
                    view?.postDelayed({ injectAll(view) }, 5000)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        view?.loadUrl(url); true
                    } else false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customView = view
                    customViewCallback = callback
                    originalOrientation = requestedOrientation
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                    val container = FrameLayout(this@MainActivity)
                    container.addView(view)
                    setContentView(container)
                }

                override fun onHideCustomView() {
                    customViewCallback?.onCustomViewHidden()
                    requestedOrientation = originalOrientation
                    setContentView(binding.root)
                    customView = null
                }
            }

            loadUrl("https://m.youtube.com")
        }
    }

    private fun injectAll(view: WebView?) {
        view?.evaluateJavascript(backgroundJS, null)
        view?.evaluateJavascript(adBlockJS, null)
    }

    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startForegroundService() {
        startService(Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_START
        })
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onResume()
        // Re-inject when going to background
        injectAll(binding.webView)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        injectAll(binding.webView)
    }

    override fun onStop() {
        super.onStop()
        binding.webView.onResume()
        injectAll(binding.webView)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                binding.webView.webChromeClient?.onHideCustomView()
                return true
            } else if (binding.webView.canGoBack()) {
                binding.webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        wakeLock?.release()
        binding.webView.destroy()
        super.onDestroy()
    }
}