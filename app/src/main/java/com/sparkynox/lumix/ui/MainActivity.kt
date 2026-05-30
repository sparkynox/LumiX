package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sparkynox.lumix.databinding.ActivityMainBinding
import com.sparkynox.lumix.helper.YtDlpHelper
import com.sparkynox.lumix.helper.YtDlpInstaller
import com.sparkynox.lumix.model.StreamInfo
import com.sparkynox.lumix.service.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val hideAdsJS = """
        (function() {
            if (window._lumiAds) return;
            window._lumiAds = true;
            const style = document.createElement('style');
            style.textContent = `
                ytd-ad-slot-renderer, ytd-banner-promo-renderer,
                ytd-statement-banner-renderer, ytd-in-feed-ad-layout-renderer,
                ytd-display-ad-renderer, #masthead-ad,
                ytd-promoted-video-renderer, ytd-promoted-sparkles-web-renderer,
                .video-ads, .ytp-ad-module, #player-ads,
                .ytp-ad-overlay-container, .ytp-ad-player-overlay,
                .ytp-ad-progress-list, .ytp-ad-skip-button-container {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                }
            `;
            document.head && document.head.appendChild(style);

            // Also skip any ad skip buttons instantly
            setInterval(() => {
                document.querySelectorAll('.ytp-ad-skip-button, .ytp-skip-ad-button')
                    .forEach(btn => { try { btn.click(); } catch(e) {} });
            }, 300);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch(Dispatchers.IO) {
            YtDlpInstaller.install(applicationContext)
        }

        // Start service immediately — all Android versions
        val serviceIntent = Intent(this, PlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setupWebView()
        setupButtons()
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
                allowContentAccess = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(hideAdsJS, null)
                    view?.postDelayed({ view.evaluateJavascript(hideAdsJS, null) }, 2000)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Intercept YouTube video — play via ExoPlayer, no WebView load
                    if (YtDlpHelper.isYouTubeUrl(url)) {
                        extractAndPlay(url)
                        return true // block WebView from loading — no ads!
                    }

                    // Allow YouTube browse + Google login
                    if (url.contains("youtube.com") || url.contains("youtu.be") ||
                        url.contains("google.com") || url.contains("accounts.google")) {
                        return false
                    }

                    // External links — open in browser
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) { }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Block YouTube's own video player completely
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    callback.onCustomViewHidden()
                }
            }

            loadUrl("https://m.youtube.com")
        }
    }

    private fun extractAndPlay(url: String) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingTitle.text = "Loading..."

        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    YtDlpHelper.extract(applicationContext, url)
                }

                binding.tvLoadingTitle.text = info.title
                playInService(info)

                binding.webView.postDelayed({
                    binding.layoutLoading.visibility = View.GONE
                }, 1500)

            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun playInService(info: StreamInfo) {
        val intent = Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY
            putExtra(PlayerService.EXTRA_URL, info.streamUrl)
            putExtra(PlayerService.EXTRA_TITLE, info.title)
            putExtra(PlayerService.EXTRA_UPLOADER, info.uploader)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}