package com.lizongying.mytv1

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.lizongying.mytv1.databinding.PlayerBinding
import com.lizongying.mytv1.models.TVModel
import java.io.ByteArrayInputStream


class WebFragment : Fragment() {
    private lateinit var mainActivity: MainActivity

    private var webView: WebView? = null
    private var tvModel: TVModel? = null
    private var webViewPool: WebViewPool? = null

    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideVolume = 2 * 1000L

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)

        webView = binding.webView

        val application = requireActivity().applicationContext as MyTVApplication
        val currentWebView = binding.webView

        currentWebView.layoutParams.width = application.shouldWidthPx()
        currentWebView.layoutParams.height = application.shouldHeightPx()

        currentWebView.settings.javaScriptEnabled = true
        currentWebView.settings.domStorageEnabled = true
        currentWebView.settings.databaseEnabled = true
        currentWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        currentWebView.settings.mediaPlaybackRequiresUserGesture = false
        currentWebView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        currentWebView.settings.userAgentString =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

//        currentWebView.settings.pluginState= WebSettings.PluginState.ON
//        currentWebView.settings.cacheMode= WebSettings.LOAD_NO_CACHE

        currentWebView.isClickable = false
        currentWebView.isFocusable = false
        currentWebView.isFocusableInTouchMode = false

        currentWebView.setOnTouchListener { v, event ->
            if (event != null) {
                (activity as MainActivity).gestureDetector.onTouchEvent(event)
            }
            true
        }

//        WebView.setWebContentsDebuggingEnabled(true)

        (activity as MainActivity).ready(TAG)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 延迟初始化WebView池，避免过早初始化导致的问题
        handler.post {
            try {
                webViewPool = WebViewPool.getInstance(requireContext().applicationContext)
                Log.i(TAG, "WebView池初始化成功")
            } catch (e: Exception) {
                Log.e(TAG, "WebView池初始化失败", e)
            }
        }
    }

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val url = tvModel.videoUrl.value as String
        Log.i(TAG, "play ${tvModel.tv.id} ${tvModel.tv.title} $url")
        
        // 优先检查是否启用预加载功能
        if (SP.preloadEnabled && webViewPool != null) {
            // 尝试使用WebView池
            tryPlayWithPool(tvModel, url)
        } else {
            // 使用原始WebView播放方式
            playWithOriginalWebView(tvModel, url)
        }
        
        // 开始预加载下一个频道
        startPreloading(tvModel)
    }
    
    private fun tryPlayWithPool(tvModel: TVModel, url: String) {
        try {
            // 释放之前的WebView
            webView?.let { oldWebView ->
                if (oldWebView.parent == binding.webViewContainer) {
                    binding.webViewContainer.removeView(oldWebView)
                }
                val oldUrl = this.tvModel?.videoUrl?.value as? String
                if (oldUrl != null && oldUrl != url) {
                    webViewPool?.releaseWebView(oldUrl)
                }
            }
            
            // 从池中获取新的WebView
            val pooledWebView = webViewPool?.getWebView(tvModel)
            if (pooledWebView != null) {
                webView = pooledWebView
                setupWebViewInContainer(pooledWebView)
                Log.i(TAG, "使用池化WebView播放: ${tvModel.tv.title}")
            } else {
                // 池化失败，降级到原始方式
                Log.w(TAG, "WebView池获取失败，降级到原始播放方式")
                playWithOriginalWebView(tvModel, url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView池播放失败，降级到原始播放方式", e)
            playWithOriginalWebView(tvModel, url)
        }
    }
    
    private fun playWithOriginalWebView(tvModel: TVModel, url: String) {
        // 使用原来的WebView播放逻辑
        webView = binding.webView
        setupWebViewInContainer(webView!!)
        
        val blockMap = mapOf(
            "央视甲" to listOf(
                "jweixin", "daohang", "dianshibao.js", "dingtalk.js", "configtool",
                "qrcode", "shareindex.js", "zhibo_shoucang.js", "gray", "cntv_Advertise.js",
                "top2023newindex.js", "indexPC.js", "getEpgInfoByChannelNew", "epglist",
                "epginfo", "getHandDataList", "2019whitetop/index.js", "pc_nav/index.js",
                "shareindex.js", "mapjs/index.js", "bottomjs/index.js", "top2023newindex.js",
                "2019dlbhyjs/index.js"
            ),
        )
        
        val scriptMap = mapOf(
            "live.kankanews.com" to R.raw.ahtv1,
            "www.cbg.cn" to R.raw.ahtv1,
            "www.sxrtv.com" to R.raw.sxrtv1,
            "www.xjtvs.com.cn" to R.raw.xjtv1,
            "www.yb983.com" to R.raw.ahtv1,
            "www.yntv.cn" to R.raw.ahtv1,
            "www.nmtv.cn" to R.raw.nmgtv1,
            "live.snrtv.com" to R.raw.ahtv1,
            "www.btzx.com.cn" to R.raw.ahtv1,
            "static.hntv.tv" to R.raw.ahtv1,
            "www.hljtv.com" to R.raw.ahtv1,
            "www.qhtb.cn" to R.raw.ahtv1,
            "www.qhbtv.com" to R.raw.ahtv1,
            "v.iqilu.com" to R.raw.ahtv1,
            "www.jlntv.cn" to R.raw.ahtv1,
            "www.cztv.com" to R.raw.ahtv1,
            "www.gzstv.com" to R.raw.ahtv1,
            "www.jxntv.cn" to R.raw.jxtv1,
            "www.hnntv.cn" to R.raw.ahtv1,
            "live.mgtv.com" to R.raw.ahtv1,
            "www.hebtv.com" to R.raw.ahtv1,
            "tc.hnntv.cn" to R.raw.ahtv1,
            "live.fjtv.net" to R.raw.ahtv1,
            "tv.gxtv.cn" to R.raw.ahtv1,
            "www.nxtv.com.cn" to R.raw.ahtv1,
            "www.ahtv.cn" to R.raw.ahtv2,
            "news.hbtv.com.cn" to R.raw.ahtv1,
            "www.sztv.com.cn" to R.raw.ahtv1,
            "www.setv.sh.cn" to R.raw.ahtv1,
            "www.gdtv.cn" to R.raw.ahtv1,
            "tv.cctv.com" to R.raw.ahtv1,
            "www.yangshipin.cn" to R.raw.ahtv1,
            "www.brtn.cn" to R.raw.xjtv1,
            "www.kangbatv.com" to R.raw.ahtv1,
            "live.jstv.com" to R.raw.xjtv1,
            "www.wfcmw.cn" to R.raw.xjtv1,
        )
        
        // 设置WebView客户端
        setupWebViewClients(webView!!, tvModel, blockMap, scriptMap, url)
        webView!!.loadUrl(url)
    }
    
    private fun setupWebViewInContainer(webView: WebView) {
        webView.setOnTouchListener { _, event ->
            if (event != null) {
                (activity as MainActivity).gestureDetector.onTouchEvent(event)
            }
            true
        }
        
        // 确保WebView在正确的容器中
        if (webView.parent != binding.webViewContainer) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            binding.webViewContainer.addView(webView)
        }
    }
    
    private fun setupWebViewClients(webView: WebView, tvModel: TVModel, blockMap: Map<String, List<String>>, scriptMap: Map<String, Int>, url: String) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage?.message() == "success") {
                    Log.i(TAG, "${tvModel.tv.title} success")
                    tvModel.tv.finished?.let {
                        webView.evaluateJavascript(it) { res ->
                            Log.i(TAG, "${tvModel.tv.title} finished: $res")
                        }
                    }
                    tvModel.setErrInfo("web ok")
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            private var finished = 0

            override fun onReceivedSslError(
                webView: WebView?,
                handler: SslErrorHandler,
                error: SslError?
            ) {
                handler.proceed()
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url

                blockMap[tvModel.tv.group]?.let { blockList ->
                    for (block in blockList) {
                        if (uri?.path?.contains(block) == true) {
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                    }
                }

                tvModel.tv.block?.let { blockList ->
                    for (block in blockList) {
                        if (uri?.path?.contains(block) == true) {
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                    }
                }

                when {
                    uri?.path?.endsWith(".css") == true -> {
                        return WebResourceResponse("text/css", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    request?.isForMainFrame == false && (
                        uri?.path?.endsWith(".jpg") == true ||
                        uri?.path?.endsWith(".jpeg") == true ||
                        uri?.path?.endsWith(".png") == true ||
                        uri?.path?.endsWith(".gif") == true ||
                        uri?.path?.endsWith(".webp") == true ||
                        uri?.path?.endsWith(".svg") == true
                    ) -> {
                        return WebResourceResponse("image/png", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }

                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                val jsCode = """
                (() => {
                    const style = document.createElement('style');
                    style.type = 'text/css';
                    style.innerHTML = `
                body { position: 'fixed'; left: '100%'; background-color: '#000'; }
                img { display: none; }
                * {
                    font-size: 0 !important; color: black !important;
                    background-color: black !important; border-color: black !important;
                    outline-color: black !important; text-shadow: none !important;
                    box-shadow: none !important; fill: black !important;
                    stroke: black !important; width: 0;
                }
                    `;
                    document.head.appendChild(style);
                })();
                """.trimIndent()
                webView.evaluateJavascript(jsCode, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (webView.progress < 100) {
                    super.onPageFinished(view, url)
                    return
                }

                finished++
                if (finished < 1) {
                    super.onPageFinished(view, url)
                    return
                }

                Log.i(TAG, "页面加载完成: ${tvModel.tv.title}")

                tvModel.tv.started?.let {
                    webView.evaluateJavascript(it, null)
                }

                super.onPageFinished(view, url)

                tvModel.tv.script?.let {
                    webView.evaluateJavascript(it, null)
                }

                val uri = Uri.parse(url)
                val script = scriptMap[uri.host] ?: R.raw.ahtv1
                var scriptContent = requireContext().resources.openRawResource(script)
                    .bufferedReader()
                    .use { it.readText() }

                tvModel.tv.id.let {
                    scriptContent = scriptContent.replace("{id}", "$it")
                }

                tvModel.tv.selector?.let {
                    scriptContent = scriptContent.replace("{selector}", it)
                }

                tvModel.tv.index?.let {
                    scriptContent = scriptContent.replace("{index}", "$it")
                }

                webView.evaluateJavascript(scriptContent, null)
            }
        }
    }
    
    private fun startPreloading(currentTvModel: TVModel) {
        // 检查是否启用预加载
        if (!SP.preloadEnabled) {
            return
        }
        
        val currentPosition = com.lizongying.mytv1.models.TVList.position.value ?: return
        val nextTvModels = mutableListOf<TVModel>()
        
        // 预加载下一个频道
        val nextPosition = if (currentPosition + 1 < com.lizongying.mytv1.models.TVList.size()) {
            currentPosition + 1
        } else {
            0
        }
        
        com.lizongying.mytv1.models.TVList.getTVModel(nextPosition)?.let {
            nextTvModels.add(it)
        }
        
        // 预加载上一个频道
        val prevPosition = if (currentPosition - 1 >= 0) {
            currentPosition - 1
        } else {
            com.lizongying.mytv1.models.TVList.size() - 1
        }
        
        com.lizongying.mytv1.models.TVList.getTVModel(prevPosition)?.let {
            nextTvModels.add(it)
        }
        
        webViewPool?.preloadNext(currentTvModel, nextTvModels)
    }

    fun showVolume(visibility: Int) {
        binding.icon.visibility = visibility
        binding.volume.visibility = visibility
        hideVolume()
    }

    fun setVolumeMax(volume: Int) {
        binding.volume.max = volume
    }

    fun setVolume(progress: Int, volume: Boolean = false) {
        val context = requireContext()
        binding.volume.progress = progress
        binding.icon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                if (volume) {
                    if (progress > 0) R.drawable.volume_up_24px else R.drawable.volume_off_24px
                } else {
                    R.drawable.light_mode_24px
                }
            )
        )
    }

    fun hideVolume() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, delayHideVolume)
    }

    fun hideVolumeNow() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, 0)
    }

    private val hideVolumeRunnable = Runnable {
        binding.icon.visibility = View.GONE
        binding.volume.visibility = View.GONE
    }

    companion object {
        private const val TAG = "WebFragment"
    }
}