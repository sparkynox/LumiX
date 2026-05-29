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

    // 🔥 SIRF AD HIDE - Background play ko touch nahi karega
    private val adHideOnlyJS = """
        (function() {
            console.log('🔥 LumiX - Ad Hide Mode Only');
            
            // Sirf ad elements hide karo
            const hideAds = () => {
                const adSelectors = [
                    '.video-ads',
                    '.ytp-ad-module', 
                    '.ytp-ad-player-overlay',
                    '.ytp-ad-image-overlay', 
                    '.ytp-ad-text-overlay',
                    '#player-ads',
                    '.ytd-display-ad-renderer',
                    '.ytd-promoted-video-renderer',
                    '.ytp-ad-overlay-container',
                    'ytd-ad-slot-renderer',
                    '.ytp-ad-progress-list',
                    '.ytp-ad-action-interstitial',
                    '.ytd-banner-promo-renderer',
                    '[aria-label*="Ad"]',
                    '.ytp-ad-simple-ad-badge'
                ];
                
                adSelectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(ad => {
                        try {
                            ad.style.display = 'none';
                            ad.style.visibility = 'hidden';
                            ad.style.height = '0px';
                            ad.style.width = '0px';
                            ad.style.opacity = '0';
                        } catch(e) {}
                    });
                });
                
                // Skip button bhi click kar do
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button').forEach(btn => {
                    if (btn.offsetParent !== null) btn.click();
                });
            };
            
            // Background play ke liye kuch mat kar, sirf ad hide kar
            setInterval(hideAds, 300);
            
            new MutationObserver(hideAds).observe(document.body, { 
                childList: true, 
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'style']
            });
            
            console.log('✅ Ad Hide Mode - Background Play Intact');
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
                    view?.evaluateJavascript(adHideOnlyJS, null)
                    // Har 5 seconds mein re-inject
                    view?.postDelayed({
                        view?.evaluateJavascript(adHideOnlyJS, null)
                    }, 2000)
                    view?.postDelayed({
                        view?.evaluateJavascript(adHideOnlyJS, null)
                    }, 5000)
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
        binding.webView.evaluateJavascript(adHideOnlyJS, null)
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