package com.sparkynox.lumix.ui

import android.annotation.SuppressLint
import android.content.Intent
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
    private var isExtracting = false

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
                .ytp-ad-progress-list, .ytp-ad-skip-button-container,
                .ytp-ce-element, .ytp-cards-teaser {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    width: 0 !important;
                }
            `;
            document.head && document.head.appendChild(style);

            // Skip button instant click
            setInterval(() => {
                document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern'
                ).forEach(btn => { try { btn.click(); } catch(e) {} });
            }, 200);

            // Block video from playing in WebView — we use ExoPlayer
            const observer = new MutationObserver(() => {
                document.querySelectorAll('video').forEach(v => {
                    if (!v._lumiBlocked) {
                        v._lumiBlocked = true;
                        v.pause();
                        v.src = '';
                        v.load();
                    }
                });
            });
            observer.observe(document.body, { childList: true, subtree: true });
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
                mediaPlaybackRequiresUserGesture = true // prevent WebView auto-playing
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
                    view?.postDelayed({ view.evaluateJavascript(hideAdsJS, null) }, 1500)
                    view?.postDelayed({ view.evaluateJavascript(hideAdsJS, null) }, 3000)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Intercept ALL YouTube video URLs
                    if (YtDlpHelper.isYouTubeUrl(url)) {
                        if (!isExtracting) extractAndPlay(url)
                        return true // block WebView completely
                    }

                    // Allow YouTube browse + Google login
                    if (url.contains("youtube.com") ||
                        url.contains("youtu.be") ||
                        url.contains("google.com") ||
                        url.contains("accounts.google") ||
                        url.contains("googleapis.com")) {
                        return false
                    }

                    // External links — browser
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (e: Exception) { }
                    return true
                }

                // Block video stream requests directly
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    // Block YouTube video streams in WebView
                    if (url.contains("googlevideo.com") ||
                        url.contains("videoplayback") ||
                        url.contains("youtubei.googleapis.com/youtubei/v1/player")) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                    return null
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Block YouTube's fullscreen player completely
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    callback.onCustomViewHidden()
                }
            }

            loadUrl("https://m.youtube.com")
        }
    }

    private fun extractAndPlay(url: String) {
        if (isExtracting) return
        isExtracting = true

        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingTitle.text = "Fetching stream..."

        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    YtDlpHelper.extract(applicationContext, url)
                }

                binding.tvLoadingTitle.text = info.title
                playInService(info)

                binding.webView.postDelayed({
                    binding.layoutLoading.visibility = View.GONE
                    isExtracting = false
                }, 1000)

            } catch (e: Exception) {
                binding.layoutLoading.visibility = View.GONE
                isExtracting = false
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