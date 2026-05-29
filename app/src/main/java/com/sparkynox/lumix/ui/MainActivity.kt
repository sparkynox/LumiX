package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
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

    // 🔥 WORKING AD BLOCKER + BACKGROUND PLAY
    private val workingJS = """
        (function() {
            console.log('🔥 LumiX - AD BLOCK + BACKGROUND ACTIVE');
            
            // ========== AD BLOCKER ==========
            // Block ad requests
            const originalFetch = window.fetch;
            window.fetch = function(url, options) {
                if (url && (url.includes('googleads') || url.includes('doubleclick') || 
                    url.includes('pagead') || url.includes('ad.doubleclick'))) {
                    console.log('Blocked ad fetch:', url);
                    return Promise.reject(new Error('Ad blocked'));
                }
                return originalFetch.apply(this, arguments);
            };
            
            // Block XHR ad requests
            const XHR = XMLHttpRequest.prototype;
            const originalOpen = XHR.open;
            XHR.open = function(method, url) {
                if (url && (url.includes('googleads') || url.includes('doubleclick') || 
                    url.includes('pagead') || url.includes('adservice'))) {
                    this._blocked = true;
                    return;
                }
                return originalOpen.apply(this, arguments);
            };
            
            // Remove ad elements continuously
            const removeAds = () => {
                const adSelectors = [
                    '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                    '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                    '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                    '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                    '.ytp-ad-overlay-container', 'ytd-ad-slot-renderer',
                    '#masthead-ad', '.ytd-banner-promo-renderer'
                ];
                adSelectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(ad => {
                        if (ad && ad.remove) ad.remove();
                        if (ad) ad.style.display = 'none';
                    });
                });
            };
            
            // Auto-skip video ads
            const skipAds = () => {
                const skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern');
                skipBtns.forEach(btn => {
                    if (btn && btn.offsetParent !== null) btn.click();
                });
            };
            
            // Mute ads only
            let adActive = false;
            const handleAdAudio = () => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay, .video-ads') !== null;
                if (video) {
                    if (isAd && !adActive) {
                        video.muted = true;
                        adActive = true;
                    } else if (!isAd && adActive) {
                        video.muted = false;
                        adActive = false;
                    }
                }
            };
            
            // ========== BACKGROUND PLAY ==========
            // Fake visibility
            Object.defineProperty(document, 'hidden', { get: () => false });
            Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
            
            // Block visibility events
            ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'].forEach(event => {
                document.addEventListener(event, (e) => {
                    e.stopPropagation();
                    e.preventDefault();
                }, true);
                window.addEventListener(event, (e) => {
                    e.stopPropagation();
                    e.preventDefault();
                }, true);
            });
            
            // Force video to keep playing
            setInterval(() => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                if (video && !isAd && video.paused && video.currentTime > 0) {
                    video.play().catch(() => {});
                }
            }, 500);
            
            // ========== RUN ALL ==========
            setInterval(() => {
                removeAds();
                skipAds();
                handleAdAudio();
            }, 200);
            
            new MutationObserver(() => {
                removeAds();
                skipAds();
                handleAdAudio();
            }).observe(document.body, { childList: true, subtree: true });
            
            console.log('✅ LumiX - Ads Blocked + Background Play Active');
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
                
                // 🔥 MOBILE USER AGENT - Fix desktop site
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(workingJS, null)
                    // Keep injecting
                    view?.postDelayed({
                        view?.evaluateJavascript(workingJS, null)
                    }, 2000)
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true
                }
            }

            // Force mobile site
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
        binding.webView.evaluateJavascript(workingJS, null)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.evaluateJavascript(workingJS, null)
    }

    override fun onStop() {
        super.onStop()
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