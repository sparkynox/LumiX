package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.sparkynox.lumix.databinding.ActivityMainBinding
import com.sparkynox.lumix.service.PlayerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var wakeLock: PowerManager.WakeLock? = null

    private val ultimateTrickJS = """
        (function() {
            console.log('🎭 LumiX Ultimate Trick Mode');
            
            // Trick 1: YouTube ko lage video download hai
            Object.defineProperty(navigator, 'onLine', { get: () => true });
            if (navigator.connection) {
                Object.defineProperty(navigator.connection, 'saveData', { get: () => true });
            }
            
            // Trick 2: Block ad requests
            const XHR = XMLHttpRequest.prototype;
            const originalOpen = XHR.open;
            XHR.open = function(method, url) {
                if (url && (url.includes('googleads') || url.includes('doubleclick') || 
                    url.includes('pagead') || url.includes('ad.doubleclick'))) {
                    this._blocked = true;
                    return;
                }
                return originalOpen.apply(this, arguments);
            };
            
            // Trick 3: Fake as YouTube app
            Object.defineProperty(navigator, 'userAgent', {
                get: () => 'com.google.android.youtube/19.01.34 (Android 14)'
            });
            
            // Trick 4: Fake visibility
            Object.defineProperty(document, 'hidden', { get: () => false });
            Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
            
            // Trick 5: Hide all ads
            const style = document.createElement('style');
            style.textContent = `
                .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                .ytd-display-ad-renderer, ytd-ad-slot-renderer,
                .ytp-ad-overlay-container, .ytd-banner-promo-renderer {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0px !important;
                }
            `;
            document.head.appendChild(style);
            
            // Trick 6: Auto skip
            setInterval(() => {
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button').forEach(btn => {
                    if (btn.offsetParent !== null) btn.click();
                });
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing');
                if (video && isAd) {
                    video.muted = true;
                    if (video.duration && video.duration < 30) video.currentTime = video.duration;
                } else if (video && video.muted && !isAd) {
                    video.muted = false;
                }
            }, 200);
            
            console.log('✅ Trick Mode Active');
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(ultimateTrickJS, null)
                    view?.postDelayed({
                        view?.evaluateJavascript(ultimateTrickJS, null)
                    }, 2000)
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true
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

    override fun onPause() {
        super.onPause()
        binding.webView.onResume()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        wakeLock?.release()
        binding.webView.destroy()
        super.onDestroy()
    }
}