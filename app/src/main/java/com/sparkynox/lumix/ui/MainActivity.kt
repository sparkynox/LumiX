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

    // 🔥 FIX POPUP - Block "Leave page" dialog
    private val fixNavigationJS = """
        (function() {
            window.addEventListener('beforeunload', function(e) {
                e.preventDefault();
                e.stopPropagation();
                delete e.returnValue;
            });
            window.confirm = function() { return true; };
            console.log('Popup fixed');
        })();
    """.trimIndent()

    // 🔥 SUPER AD BLOCKER
    private val superAdBlockJS = """
        (function() {
            console.log('🔥 LumiX Super Ad Blocker Active');
            
            const killAllAds = () => {
                const selectors = [
                    '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                    '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                    '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                    '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                    '.ytp-ad-overlay-container', '.ytp-ad-skip-button-container',
                    'ytd-ad-slot-renderer', '#masthead-ad', '.ad-container',
                    '[class*="-ad-"]', '[id*="-ad-"]'
                ];
                
                selectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(ad => {
                        try {
                            ad.remove();
                            ad.style.display = 'none';
                            ad.style.visibility = 'hidden';
                            ad.style.height = '0px';
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
                        if (video.duration && video.duration < 30 && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                    } else if (!video.muted && !video.paused) {
                        video.muted = false;
                        video.volume = 1;
                    }
                }
            };
            
            const clickSkip = () => {
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern').forEach(btn => {
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
                    opacity: 0 !important;
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
    """.trimIndent()

    // 🔥 BACKGROUND PLAY - Never stops
    private val superBackgroundJS = """
        (function() {
            console.log('🎵 LumiX Background Player Active');
            
            Object.defineProperty(document, 'hidden', { get: () => false });
            Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
            
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
            
            const keepPlaying = () => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                if (video && !isAd && video.paused && !video.ended && video.currentTime > 0) {
                    video.play().catch(e => console.log('Play blocked:', e));
                }
            };
            
            if (document.querySelector('video')) {
                const originalPause = video.pause;
                video.pause = function() {
                    const isAd = document.querySelector('.ad-showing');
                    if (!isAd) return;
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
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 🔥 MOBILE USER AGENT - Fix desktop site issue
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject all scripts in correct order
                    view?.evaluateJavascript(fixNavigationJS, null)      // Fix popup first
                    view?.evaluateJavascript(superBackgroundJS, null)    // Then background
                    view?.evaluateJavascript(superAdBlockJS, null)       // Then ad blocker
                    
                    // Re-inject after 2 seconds for safety
                    view?.postDelayed({
                        view?.evaluateJavascript(superAdBlockJS, null)
                        view?.evaluateJavascript(superBackgroundJS, null)
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

            // Load mobile YouTube
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