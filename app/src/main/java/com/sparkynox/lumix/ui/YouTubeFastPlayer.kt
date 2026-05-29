package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
                
                const killAllAds = () => {
                    const selectors = [
                        '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                        '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                        '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                        '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                        '.ytp-ad-overlay-container', '.ytp-ad-skip-button-container',
                        'ytd-ad-slot-renderer', '#masthead-ad', '.ad-container'
                    ];
                    
                    selectors.forEach(sel => {
                        document.querySelectorAll(sel).forEach(ad => {
                            try {
                                ad.remove();
                                ad.style.display = 'none';
                            } catch(e) {}
                        });
                    });
                };
                
                const controlAudio = () => {
                    const video = document.querySelector('video');
                    const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay') !== null;
                    
                    if (video) {
                        if (isAd) {
                            video.muted = true;
                            video.volume = 0;
                        } else if (!video.muted && !video.paused) {
                            video.muted = false;
                            video.volume = 1;
                        }
                    }
                };
                
                const clickSkip = () => {
                    document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button').forEach(btn => {
                        if (btn.offsetParent !== null) btn.click();
                    });
                };
                
                const style = document.createElement('style');
                style.textContent = `
                    .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                    .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                    .ytd-display-ad-renderer, .ytp-ad-overlay-container {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0px !important;
                    }
                `;
                document.head.appendChild(style);
                
                let last = 0;
                function loop(time) {
                    if (time - last > 200) {
                        killAllAds();
                        controlAudio();
                        clickSkip();
                        last = time;
                    }
                    requestAnimationFrame(loop);
                }
                requestAnimationFrame(loop);
                
                new MutationObserver(() => { killAllAds(); clickSkip(); })
                    .observe(document.body, { childList: true, subtree: true });
                    
                console.log('✅ LumiX Ad Blocker Ready');
            })();
        """
        
        private const val BACKGROUND_JS = """
            (function() {
                Object.defineProperty(document, 'hidden', { get: () => false });
                Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
                
                const blockEvent = (e) => {
                    e.stopImmediatePropagation();
                    e.preventDefault();
                };
                
                document.addEventListener('visibilitychange', blockEvent, true);
                window.addEventListener('blur', blockEvent, true);
                window.addEventListener('pagehide', blockEvent, true);
                
                setInterval(() => {
                    const video = document.querySelector('video');
                    const isAd = document.querySelector('.ad-showing');
                    if (video && !isAd && video.paused && video.currentTime > 0) {
                        video.play().catch(() => {});
                    }
                }, 500);
                
                console.log('✅ LumiX Background Player Ready');
            })();
        """
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_player)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LumiX:BackgroundPlay")
        wakeLock?.acquire(10*60*1000L)

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
            
            // Critical for background playback
            mediaPlaybackRequiresUserGesture = false
            
            // Desktop mode
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(BACKGROUND_JS, null)
                view?.evaluateJavascript(AD_BLOCKER_JS, null)
                view?.postDelayed({
                    view?.evaluateJavascript(AD_BLOCKER_JS, null)
                }, 2000)
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
                webView.webChromeClient?.onHideCustomView()
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
            webView.loadUrl("about:blank")
        }
        wakeLock?.release()
        webView.destroy()
        super.onDestroy()
    }
}