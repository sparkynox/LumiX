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

    // 🔥 100% AD BLOCK + HIDE + MUTE (Video sound intact)
    private val ultimateAdBlockJS = """
        (function() {
            console.log('🔥 100% Ad Blocker Active');
            
            // ===== HIDE ALL ADS =====
            const hideAllAds = () => {
                const adSelectors = [
                    '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay',
                    '.ytp-ad-image-overlay', '.ytp-ad-text-overlay', '#player-ads',
                    '.ytd-display-ad-renderer', '.ytd-promoted-video-renderer',
                    '.ytp-ad-progress-list', '.ytp-ad-action-interstitial',
                    '.ytp-ad-overlay-container', 'ytd-ad-slot-renderer',
                    '.ytp-ad-skip-button-container', '.ytd-banner-promo-renderer',
                    '.ytp-ad-simple-ad-badge', '.ytp-ad-alert-message',
                    'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]',
                    '#masthead-ad', '.ad-container', '[class*="-ad-"]'
                ];
                
                adSelectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(ad => {
                        try {
                            ad.remove();
                            ad.style.display = 'none';
                            ad.style.visibility = 'hidden';
                            ad.style.height = '0px';
                            ad.style.width = '0px';
                            ad.style.opacity = '0';
                        } catch(e) {}
                    });
                });
            };
            
            // ===== SKIP ADS AUTOMATICALLY =====
            const skipAdButtons = () => {
                const skipSelectors = [
                    '.ytp-ad-skip-button',
                    '.ytp-skip-ad-button', 
                    '.ytp-ad-skip-button-modern',
                    '.ytp-skip-ad-button__text'
                ];
                
                skipSelectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(btn => {
                        if (btn && btn.offsetParent !== null) {
                            btn.click();
                            console.log('Skipped ad');
                        }
                    });
                });
            };
            
            // ===== MUTE ONLY ADS, VIDEO SOUND INTACT =====
            let adPlaying = false;
            
            const muteAdsOnly = () => {
                const video = document.querySelector('video');
                const isAdActive = document.querySelector('.ad-showing, .ytp-ad-player-overlay, .video-ads') !== null;
                
                if (video) {
                    if (isAdActive && !adPlaying) {
                        // Ad started - mute
                        video.muted = true;
                        adPlaying = true;
                        console.log('Ad playing - muted');
                    } else if (!isAdActive && adPlaying) {
                        // Ad ended - unmute
                        video.muted = false;
                        adPlaying = false;
                        console.log('Ad ended - sound restored');
                    }
                }
            };
            
            // ===== CSS INSTANT HIDE =====
            const style = document.createElement('style');
            style.textContent = `
                .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                .ytp-ad-image-overlay, .ytp-ad-text-overlay, #player-ads,
                .ytd-display-ad-renderer, .ytp-ad-overlay-container,
                ytd-ad-slot-renderer, .ytd-banner-promo-renderer {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0px !important;
                    width: 0px !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
            `;
            document.head.appendChild(style);
            
            // ===== RUN EVERYTHING =====
            setInterval(() => {
                hideAllAds();
                skipAdButtons();
                muteAdsOnly();
            }, 150);
            
            // Watch for new ads
            new MutationObserver(() => {
                hideAllAds();
                skipAdButtons();
                muteAdsOnly();
            }).observe(document.body, { childList: true, subtree: true, attributes: true });
            
            console.log('✅ 100% Ad Block - Hide + Mute, Video sound intact');
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
                setMediaPlaybackRequiresUserGesture(false)
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(ultimateAdBlockJS, null)
                    view?.postDelayed({
                        view?.evaluateJavascript(ultimateAdBlockJS, null)
                    }, 1000)
                    view?.postDelayed({
                        view?.evaluateJavascript(ultimateAdBlockJS, null)
                    }, 3000)
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
        binding.webView.evaluateJavascript(ultimateAdBlockJS, null)
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