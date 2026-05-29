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
            // Trick YouTube — page is always visible
            Object.defineProperty(document, 'hidden', { get: () => false });
            Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });

            const block = (e) => { e.stopImmediatePropagation(); e.preventDefault(); };
            document.addEventListener('visibilitychange', block, true);
            window.addEventListener('blur', block, true);
            window.addEventListener('pagehide', block, true);

            // Auto-resume if YouTube pauses (only real video, not ad)
            setInterval(() => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing');
                if (video && !isAd && video.paused && video.currentTime > 0 && !video.ended) {
                    video.play().catch(() => {});
                }
            }, 500);
        })();
    """.trimIndent()

    private val adBlockJS = """
        (function() {
            const killAds = () => {
                [
                    '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                    '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                    '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                    '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                    '.ytp-ad-overlay-container', 'ytd-ad-slot-renderer',
                    '#masthead-ad', '.ad-container', 'ytd-banner-promo-renderer'
                ].forEach(sel => {
                    document.querySelectorAll(sel).forEach(el => {
                        try { el.remove(); el.style.display = 'none'; } catch(e) {}
                    });
                });
            };

            const muteAd = () => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                if (video) {
                    if (isAd) {
                        video.muted = true;
                        video.volume = 0;
                        // Try skip by jumping to end
                        if (video.duration && !isNaN(video.duration)) {
                            try { video.currentTime = video.duration; } catch(e) {}
                        }
                    } else {
                        video.muted = false;
                        video.volume = 1;
                    }
                }
            };

            const skipAd = () => {
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button').forEach(btn => {
                    if (btn.offsetParent !== null) btn.click();
                });
            };

            // CSS nuke
            const style = document.createElement('style');
            style.textContent = `
                .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                .ytd-display-ad-renderer, .ytp-ad-overlay-container,
                ytd-ad-slot-renderer, #masthead-ad {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                }
            `;
            if (document.head) document.head.appendChild(style);

            // requestAnimationFrame loop — smooth and fast
            let last = 0;
            function loop(t) {
                if (t - last > 200) {
                    killAds(); muteAd(); skipAd();
                    last = t;
                }
                requestAnimationFrame(loop);
            }
            requestAnimationFrame(loop);

            new MutationObserver(() => { killAds(); skipAd(); })
                .observe(document.body, { childList: true, subtree: true });
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen + CPU alive
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
                    // Background JS first — then ad block
                    view?.evaluateJavascript(backgroundJS, null)
                    view?.evaluateJavascript(adBlockJS, null)
                    // Inject again after 2s — YouTube lazy loads stuff
                    view?.postDelayed({
                        view.evaluateJavascript(backgroundJS, null)
                        view.evaluateJavascript(adBlockJS, null)
                    }, 2000)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        view?.loadUrl(url)
                        true
                    } else false
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Fullscreen video support
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

    // CRITICAL — never let WebView pause
    override fun onPause() {
        super.onPause()
        binding.webView.onResume()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.evaluateJavascript(backgroundJS, null)
    }

    override fun onStop() {
        super.onStop()
        binding.webView.onResume()
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