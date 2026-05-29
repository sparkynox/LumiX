package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sparkynox.lumix.R

class YouTubeFastPlayer : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val AD_BLOCKER_JS = """
            (function() {
                console.log('🔥 LumiX Ultimate Ad Blocker Active');
                
                // Remove ALL ads instantly
                const removeAds = () => {
                    const adSelectors = [
                        '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                        '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                        '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                        '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                        '.ytp-ad-overlay-container', '.ytp-ad-skip-button-container',
                        'ytd-ad-slot-renderer', 'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]',
                        '.ytp-ad-simple-ad-badge', '.ytp-ad-advertiser-info', '.ytp-ad-player-overlay-internal'
                    ];
                    
                    adSelectors.forEach(selector => {
                        document.querySelectorAll(selector).forEach(ad => {
                            try {
                                ad.remove();
                                ad.style.display = 'none';
                                ad.style.visibility = 'hidden';
                            } catch(e) {}
                        });
                    });
                };
                
                // Mute ads, unmute real videos
                const handleAudio = () => {
                    const video = document.querySelector('video');
                    const isAdShowing = document.querySelector('.ad-showing, .ytp-ad-player-overlay, .video-ads') !== null;
                    
                    if (video) {
                        if (isAdShowing) {
                            video.muted = true;
                            video.volume = 0;
                        } else if (!video.muted && !isAdShowing && !video.paused) {
                            video.muted = false;
                            video.volume = 1;
                        }
                    }
                };
                
                // Auto skip ads
                const skipAd = () => {
                    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern');
                    if (skipBtn && skipBtn.offsetParent !== null) {
                        skipBtn.click();
                    }
                    
                    // Try to seek to end of unskippable ads
                    const video = document.querySelector('video');
                    if (video && document.querySelector('.ad-showing') && video.duration - video.currentTime < 5) {
                        video.currentTime = video.duration;
                    }
                };
                
                // CSS injection for instant hiding
                const style = document.createElement('style');
                style.textContent = `
                    .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                    .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                    .ytd-display-ad-renderer, [class*="-ad-"], [class*="ad-"] {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0px !important;
                        opacity: 0 !important;
                    }
                `;
                document.head.appendChild(style);
                
                // Run continuously but efficiently
                let lastRun = 0;
                function loop(time) {
                    if (time - lastRun > 200) {
                        removeAds();
                        handleAudio();
                        skipAd();
                        lastRun = time;
                    }
                    requestAnimationFrame(loop);
                }
                requestAnimationFrame(loop);
                
                // Mutation observer for dynamically loaded ads
                const observer = new MutationObserver(() => {
                    removeAds();
                    skipAd();
                });
                observer.observe(document.body, { childList: true, subtree: true });
                
                console.log('✅ LumiX Ad Blocker Ready - Background Play Enabled');
            })();
        """
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_player)

        // Keep screen on and CPU awake for background play
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Wake lock for background playback
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LumiX:BackgroundPlay")
        wakeLock?.acquire(10*60*1000L) // 10 minutes

        setupWebView()
        
        val videoUrl = intent.getStringExtra("url") ?: "https://m.youtube.com"
        webView.loadUrl(videoUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
            
            // CRITICAL for background playback
            mediaPlaybackRequiresUserGesture = false
            setMediaPlaybackRequiresUserGesture(false)
            
            // Desktop mode for better experience
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            
            // Performance settings
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(AD_BLOCKER_JS, null)
                // Re-inject after 1 second for safety
                view?.postDelayed({
                    view.evaluateJavascript(AD_BLOCKER_JS, null)
                }, 1000)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                    view?.loadUrl(url)
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                originalOrientation = requestedOrientation
                customView = view
                customViewCallback = callback
                
                supportActionBar?.hide()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                
                val decorView = window.decorView
                decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                
                val container = FrameLayout(this@YouTubeFastPlayer)
                container.addView(view)
                setContentView(container)
            }

            override fun onHideCustomView() {
                customViewCallback?.onCustomViewHidden()
                requestedOrientation = originalOrientation
                setContentView(R.layout.activity_youtube_player)
                setupWebView()
                supportActionBar?.show()
                customView = null
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            removeAllCookies(null)
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.webChromeClient.onHideCustomView()
                return true
            } else if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (customView == null && webView.url != null) {
            webView.loadUrl(webView.url!!)
        }
    }

    override fun onDestroy() {
        if (!isFinishing) {
            // Keep playing in background
            webView.loadUrl("about:blank")
        }
        wakeLock?.release()
        webView.destroy()
        super.onDestroy()
    }
}