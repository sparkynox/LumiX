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

    private val removeDialogsJS = """
        (function() {
            const removeDialogs = () => {
                const selectors = ['.yt-dialog', '.yt-confirm-dialog', '#dialog', '.modal', '[role="dialog"]', 'tp-yt-paper-dialog'];
                selectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(d => { d.remove(); d.style.display = 'none'; });
                });
                document.querySelectorAll('button').forEach(btn => {
                    if (btn.innerText === 'LEAVE' || btn.innerText === 'STAY') btn.click();
                });
            };
            removeDialogs();
            window.confirm = () => true;
            window.alert = () => true;
            new MutationObserver(() => removeDialogs()).observe(document.body, { childList: true, subtree: true });
        })();
    """.trimIndent()

    private val superAdBlockJS = """
        (function() {
            console.log('🔥 LumiX ULTIMATE Ad Blocker');
            
            const adSelectors = [
                '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                '.ytp-ad-overlay-container', 'ytd-ad-slot-renderer', '#masthead-ad',
                '.ytp-ad-skip-button-container', 'ytd-in-feed-ad-layout-renderer',
                '[aria-label*="Ad"]', '[aria-label*="Sponsored"]'
            ];
            
            const removeAllAds = () => {
                adSelectors.forEach(selector => {
                    document.querySelectorAll(selector).forEach(ad => {
                        ad.remove();
                        ad.style.display = 'none';
                        ad.style.visibility = 'hidden';
                    });
                });
            };
            
            const forceSkipAd = () => {
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button').forEach(btn => {
                    if (btn.offsetParent !== null) btn.click();
                });
            };
            
            let adActive = false;
            
            const handleAudio = () => {
                const video = document.querySelector('video');
                const isAdPlaying = document.querySelector('.ad-showing, .ytp-ad-player-overlay, .video-ads') !== null;
                
                if (video) {
                    if (isAdPlaying) {
                        if (!adActive) {
                            video.muted = true;
                            adActive = true;
                        }
                    } else if (adActive) {
                        video.muted = false;
                        adActive = false;
                        console.log('Sound restored');
                    }
                }
            };
            
            const style = document.createElement('style');
            style.textContent = `${adSelectors.map(s => `${s}{display:none!important;visibility:hidden!important;height:0!important;}`).join('')}`;
            document.head.appendChild(style);
            
            setInterval(() => {
                removeAllAds();
                forceSkipAd();
                handleAudio();
            }, 300);
            
            new MutationObserver(() => { removeAllAds(); forceSkipAd(); handleAudio(); })
                .observe(document.body, { childList: true, subtree: true });
                
            console.log('✅ Ad Blocker Ready');
        })();
    """.trimIndent()

    private val superBackgroundJS = """
        (function() {
            Object.defineProperty(document, 'hidden', { get: () => false });
            Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
            
            const blockEvent = (e) => { e.stopImmediatePropagation(); e.preventDefault(); return false; };
            document.addEventListener('visibilitychange', blockEvent, true);
            window.addEventListener('blur', blockEvent, true);
            
            setInterval(() => {
                const video = document.querySelector('video');
                const isAd = document.querySelector('.ad-showing');
                if (video && !isAd && video.paused && video.currentTime > 0) {
                    video.play().catch(() => {});
                }
            }, 500);
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
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(removeDialogsJS, null)
                    view?.evaluateJavascript(superBackgroundJS, null)
                    view?.evaluateJavascript(superAdBlockJS, null)
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