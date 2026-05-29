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

    // 🔥 THE REAL BACKGROUND PLAY + AD BLOCK
    private val theRealJS = """
        (function() {
            console.log('🔥 THE REAL DEAL - Background + Ad Block');
            
            // ===== THE REAL BACKGROUND PLAY TRICK =====
            // YouTube checks these 3 things to stop video:
            // 1. document.hidden
            // 2. document.visibilityState  
            // 3. window.blur event
            
            // Block ALL of them
            Object.defineProperty(document, 'hidden', { 
                get: function() { return false; },
                configurable: false
            });
            
            Object.defineProperty(document, 'visibilityState', { 
                get: function() { return 'visible'; },
                configurable: false 
            });
            
            // Kill ALL pause events
            const blockAllPauseEvents = () => {
                const video = document.querySelector('video');
                if (video) {
                    // Override pause method completely
                    const originalPause = video.pause;
                    video.pause = function() {
                        const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                        if (isAd) {
                            return originalPause.call(this);
                        }
                        console.log('Blocked pause - playing in background');
                        return false;
                    };
                    
                    // Force play if paused
                    setInterval(() => {
                        if (video.paused && !video.ended && video.currentTime > 0) {
                            const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                            if (!isAd) {
                                video.play().catch(e => console.log('Play error:', e));
                            }
                        }
                    }, 200);
                } else {
                    setTimeout(blockAllPauseEvents, 100);
                }
            };
            
            // Block all visibility change events at root level
            const events = ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide', 'focus', 'resize'];
            events.forEach(eventType => {
                window.addEventListener(eventType, (e) => {
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    e.preventDefault();
                    return false;
                }, true);
                
                document.addEventListener(eventType, (e) => {
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    e.preventDefault();
                    return false;
                }, true);
            });
            
            // Force document to always be visible
            const forceVisible = () => {
                if (document.hidden || document.visibilityState !== 'visible') {
                    Object.defineProperty(document, 'hidden', { get: () => false });
                    Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
                }
            };
            
            setInterval(forceVisible, 100);
            
            // Start the video protection
            setTimeout(blockAllPauseEvents, 500);
            
            // ===== AD BLOCK =====
            const blockAds = () => {
                // Hide ad elements
                document.querySelectorAll('.video-ads, .ytp-ad-module, .ytp-ad-player-overlay, .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads, .ytd-display-ad-renderer, .ytd-promoted-video-renderer, ytd-ad-slot-renderer, .ytp-ad-overlay-container').forEach(ad => {
                    ad.remove();
                    ad.style.display = 'none';
                });
                
                // Skip buttons
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button').forEach(btn => {
                    if (btn.offsetParent !== null) btn.click();
                });
                
                // Mute ads only
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                if (video && isAd && !video.muted) {
                    video.muted = true;
                } else if (video && !isAd && video.muted) {
                    video.muted = false;
                }
            };
            
            setInterval(blockAds, 200);
            new MutationObserver(blockAds).observe(document.body, { childList: true, subtree: true });
            
            // Add style to hide ads
            const style = document.createElement('style');
            style.textContent = `
                .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                .ytd-display-ad-renderer, ytd-ad-slot-renderer,
                .ytp-ad-overlay-container {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0px !important;
                    width: 0px !important;
                }
            `;
            document.head.appendChild(style);
            
            console.log('✅ THE REAL DEAL - Background play WILL work!');
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
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(theRealJS, null)
                    // Keep re-injecting to ensure it sticks
                    view?.postDelayed({
                        view?.evaluateJavascript(theRealJS, null)
                    }, 1000)
                    view?.postDelayed({
                        view?.evaluateJavascript(theRealJS, null)
                    }, 3000)
                    view?.postDelayed({
                        view?.evaluateJavascript(theRealJS, null)
                    }, 5000)
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {}

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

    // CRITICAL FOR BACKGROUND PLAY - Do NOT let WebView pause
    override fun onPause() {
        super.onPause()
        // This is the secret - keep WebView alive
        binding.webView.onResume()
        binding.webView.loadUrl("javascript:document.querySelector('video')?.play();")
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.evaluateJavascript(theRealJS, null)
    }

    override fun onStop() {
        super.onStop()
        // Most important - don't stop the WebView
        binding.webView.onResume()
        binding.webView.loadUrl("javascript:(function(){document.querySelector('video')?.play();})();")
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