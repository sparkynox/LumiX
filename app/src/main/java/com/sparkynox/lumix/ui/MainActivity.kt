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

    // 🔥 SUPER CHARGED AD BLOCKER - Full power!
    private val superAdBlockJS = """
        (function() {
            console.log('🔥 LumiX Super Ad Blocker Active');
            
            // Kill ALL ads instantly
            const killAllAds = () => {
                // CSS selectors for all known YouTube ads
                const selectors = [
                    '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                    '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                    '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                    '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                    '.ytp-ad-overlay-container', '.ytp-ad-skip-button-container',
                    'ytd-ad-slot-renderer', 'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]',
                    '.ytp-ad-simple-ad-badge', '.ytp-ad-advertiser-info',
                    '#masthead-ad', 'ytd-banner-promo-renderer',
                    'ytd-video-masthead-ad-v3-renderer', 'ytd-in-feed-ad-layout-renderer',
                    'ytd-statement-banner-renderer', 'ytd-promoted-sparkles-web-renderer',
                    '.ad-container', '[class*="-ad-"]', '[id*="-ad-"]'
                ];
                
                selectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(ad => {
                        try {
                            ad.remove();
                            ad.style.display = 'none';
                            ad.style.visibility = 'hidden';
                            ad.style.height = '0px';
                            ad.style.width = '0px';
                        } catch(e) {}
                    });
                });
            };
            
            // Mute ads, unmute real videos
            const controlAudio = () => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay, .video-ads') !== null;
                
                if (video) {
                    if (isAd) {
                        video.muted = true;
                        video.volume = 0;
                        // Fast forward through ad if possible
                        if (video.duration && video.duration < 30 && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                    } else if (!video.muted) {
                        // Only unmute if video is playing and not an ad
                        if (!video.paused) {
                            video.muted = false;
                            video.volume = 1;
                        }
                    }
                }
            };
            
            // Auto skip any ad button
            const clickSkip = () => {
                const btns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button__text');
                btns.forEach(btn => {
                    if (btn.offsetParent !== null) btn.click();
                });
            };
            
            // Inject CSS to hide ads
            const style = document.createElement('style');
            style.textContent = `
                .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                .ytd-display-ad-renderer, [class*="-ad-"],
                .ytp-ad-overlay-container {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0px !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
            `;
            document.head.appendChild(style);
            
            // Run continuously
            let last = 0;
            function superLoop(time) {
                if (time - last > 150) {
                    killAllAds();
                    controlAudio();
                    clickSkip();
                    last = time;
                }
                requestAnimationFrame(superLoop);
            }
            requestAnimationFrame(superLoop);
            
            // Watch for new elements
            const observer = new MutationObserver(() => {
                killAllAds();
                clickSkip();
            });
            observer.observe(document.body, { childList: true, subtree: true });
            
            console.log('✅ LumiX Ultimate Ad Blocker Ready');
        })();
    """.trimIndent()

    // 🔥 IMPROVED BACKGROUND PLAY - Never stops!
    private val superBackgroundJS = """
        (function() {
            console.log('🎵 LumiX Background Player Active');
            
            // Fake visibility state - YouTube can't know we're in background
            Object.defineProperty(document, 'hidden', { get: () => false });
            Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
            
            // Block all visibility change events
            const blockEvent = (e) => {
                e.stopImmediatePropagation();
                e.preventDefault();
                return false;
            };
            
            document.addEventListener('visibilitychange', blockEvent, true);
            document.addEventListener('webkitvisibilitychange', blockEvent, true);
            window.addEventListener('blur', blockEvent, true);
            window.addEventListener('pagehide', blockEvent, true);
            window.addEventListener('beforeunload', blockEvent, true);
            
            // Force video to keep playing
            const keepPlaying = () => {
                const video = document.querySelector('video');
                if (video && !video.paused && !video.ended && !video.seeking) {
                    // Video is playing - do nothing
                    return;
                }
                
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                if (video && !isAd && video.paused && !video.ended && video.currentTime > 0) {
                    video.play().catch(e => console.log('Play blocked:', e));
                }
            };
            
            // Override pause method
            const video = document.querySelector('video');
            if (video) {
                const originalPause = video.pause;
                video.pause = function() {
                    const isAd = document.querySelector('.ad-showing');
                    if (!isAd) {
                        console.log('Blocked pause attempt');
                        return;
                    }
                    return originalPause.call(this);
                };
            }
            
            setInterval(keepPlaying, 500);
            console.log('✅ LumiX Background Player Ready');
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
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LumiX::BackgroundPlay"
        )
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
                setSupportZoom(false)
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject both scripts
                    view?.evaluateJavascript(superBackgroundJS, null)
                    view?.evaluateJavascript(superAdBlockJS, null)
                    
                    // Re-inject after 2 seconds for safety
                    view?.postDelayed({
                        view.evaluateJavascript(superAdBlockJS, null)
                    }, 2000)
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 80) {
                        view?.evaluateJavascript(superAdBlockJS, null)
                    }
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
        // Keep WebView alive!
        binding.webView.onResume()
        // Re-inject background script
        binding.webView.evaluateJavascript(superBackgroundJS, null)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.evaluateJavascript(superBackgroundJS, null)
        binding.webView.evaluateJavascript(superAdBlockJS, null)
    }

    override fun onStop() {
        super.onStop()
        // CRITICAL: Don't pause WebView
        binding.webView.onResume()
        binding.webView.evaluateJavascript(superBackgroundJS, null)
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