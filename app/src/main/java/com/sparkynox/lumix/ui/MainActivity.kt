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

    private val adBlockJS = """
        (function() {
            function skipAd() {
                var skipBtn = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, button.ytp-ad-skip-button-modern');
                if (skipBtn) skipBtn.click();
            }

            function nukeAds() {
                var adSelectors = [
                    '.ytp-ad-module', '.ytp-ad-overlay-container',
                    '.ytp-ad-text-overlay', '.ytp-ad-image-overlay',
                    '.ytp-ad-player-overlay', '.ytp-ad-action-interstitial',
                    '#masthead-ad', '#player-ads',
                    'ytd-banner-promo-renderer', 'ytd-video-masthead-ad-v3-renderer',
                    'ytd-in-feed-ad-layout-renderer', 'ytd-ad-slot-renderer',
                    'ytd-statement-banner-renderer', 'ytd-display-ad-renderer',
                    'ytd-promoted-sparkles-web-renderer', 'ytd-promoted-video-renderer'
                ];
                adSelectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('height', '0', 'important');
                        el.style.setProperty('width', '0', 'important');
                    });
                });

                var video = document.querySelector('video');
                if (video) {
                    var isAd = document.querySelector('.ad-showing');
                    if (isAd) {
                        video.muted = true;
                        video.volume = 0;
                        if (video.duration && !isNaN(video.duration) && video.duration > 0) {
                            try { video.currentTime = video.duration; } catch(e) {}
                        }
                    } else {
                        if (video.muted) { video.muted = false; video.volume = 1; }
                    }
                }
            }

            setInterval(function() { skipAd(); nukeAds(); }, 300);

            var observer = new MutationObserver(function() { skipAd(); nukeAds(); });
            observer.observe(document.body, { childList: true, subtree: true });
        })();
    """.trimIndent()

    private val backgroundPlayJS = """
        (function() {
            Object.defineProperty(document, 'hidden', { get: function() { return false; } });
            Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
            document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
            document.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
            window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
            window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);

            // Keep video playing
            var video = document.querySelector('video');
            if (video) {
                video.addEventListener('pause', function() {
                    setTimeout(function() {
                        var isAd = document.querySelector('.ad-showing');
                        if (!isAd) video.play();
                    }, 200);
                });
            }
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
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max
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
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(backgroundPlayJS, null)
                    view?.evaluateJavascript(adBlockJS, null)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 50) view?.evaluateJavascript(adBlockJS, null)
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

    // CRITICAL — don't pause WebView when app goes to background
    override fun onPause() {
        super.onPause()
        binding.webView.onResume() // keep it alive!
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.evaluateJavascript(backgroundPlayJS, null)
    }

    override fun onStop() {
        super.onStop()
        binding.webView.onResume() // still keep alive
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