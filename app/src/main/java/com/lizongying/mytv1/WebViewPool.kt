package com.lizongying.mytv1

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import com.lizongying.mytv1.models.TVModel
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class WebViewPool private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "WebViewPool"
        private const val POOL_SIZE = 3
        private const val PRELOAD_COUNT = 2
        
        @Volatile
        private var INSTANCE: WebViewPool? = null
        
        fun getInstance(context: Context): WebViewPool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebViewPool(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val availableWebViews = ConcurrentLinkedQueue<WebView>()
    private val busyWebViews = ConcurrentHashMap<String, WebView>()
    private val preloadedWebViews = ConcurrentHashMap<String, WebView>()
    private val handler = Handler(Looper.getMainLooper())
    
    // 脚本映射 (从原WebFragment复制)
    private val scriptMap = mapOf(
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
    
    private val blockMap = mapOf(
        "央视甲" to listOf(
            "jweixin", "daohang", "dianshibao.js", "dingtalk.js", "configtool",
            "qrcode", "shareindex.js", "zhibo_shoucang.js", "gray", "cntv_Advertise.js",
            "top2023newindex.js", "indexPC.js", "getEpgInfoByChannelNew", "epglist",
            "epginfo", "getHandDataList", "2019whitetop/index.js", "pc_nav/index.js",
            "shareindex.js", "mapjs/index.js", "bottomjs/index.js", "top2023newindex.js",
            "2019dlbhyjs/index.js"
        ),
    )
    
    init {
        initializePool()
    }
    
    private fun initializePool() {
        repeat(POOL_SIZE) {
            val webView = createWebView()
            availableWebViews.offer(webView)
        }
        Log.i(TAG, "WebView池初始化完成，池大小: $POOL_SIZE")
    }
    
    private fun createWebView(): WebView {
        val webView = WebView(context)
        val application = context as? MyTVApplication
        
        // 设置布局参数
        if (application != null) {
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                application.shouldWidthPx(),
                application.shouldHeightPx()
            )
            webView.layoutParams = layoutParams
        } else {
            // 如果无法获取应用实例，使用默认的全屏布局参数
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            webView.layoutParams = layoutParams
        }
        
        // WebView基础配置
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
            
            // 缓存优化 - 使用现代化的缓存设置
            cacheMode = WebSettings.LOAD_DEFAULT
            // Application Cache API 已在较新版本中弃用，使用DOM存储和数据库缓存替代
            domStorageEnabled = true
            databaseEnabled = true
        }
        
        webView.isClickable = false
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        
        return webView
    }
    
    fun getWebView(tvModel: TVModel): WebView? {
        val url = tvModel.videoUrl.value as? String ?: return null
        
        // 检查是否有预加载的WebView
        preloadedWebViews[url]?.let { webView ->
            preloadedWebViews.remove(url)
            busyWebViews[url] = webView
            Log.i(TAG, "使用预加载的WebView: ${tvModel.tv.title}")
            return webView
        }
        
        // 从池中获取可用的WebView
        val webView = availableWebViews.poll() ?: createWebView()
        busyWebViews[url] = webView
        
        setupWebViewForTV(webView, tvModel)
        return webView
    }
    
    fun releaseWebView(url: String) {
        busyWebViews.remove(url)?.let { webView ->
            cleanWebView(webView)
            availableWebViews.offer(webView)
            Log.i(TAG, "WebView已释放回池")
        }
    }
    
    fun preloadNext(currentTvModel: TVModel, nextTvModels: List<TVModel>) {
        handler.post {
            nextTvModels.take(PRELOAD_COUNT).forEach { tvModel ->
                val url = tvModel.videoUrl.value as? String ?: return@forEach
                
                if (preloadedWebViews.containsKey(url) || busyWebViews.containsKey(url)) {
                    return@forEach
                }
                
                val webView = availableWebViews.poll() ?: return@forEach
                preloadedWebViews[url] = webView
                
                Log.i(TAG, "开始预加载: ${tvModel.tv.title}")
                setupWebViewForTV(webView, tvModel, preload = true)
            }
        }
    }
    
    private fun setupWebViewForTV(webView: WebView, tvModel: TVModel, preload: Boolean = false) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage?.message() == "success") {
                    Log.i(TAG, "${tvModel.tv.title} success (预加载: $preload)")
                    if (!preload) {
                        tvModel.tv.finished?.let {
                            webView.evaluateJavascript(it) { res ->
                                Log.i(TAG, "${tvModel.tv.title} finished: $res")
                            }
                        }
                        tvModel.setErrInfo("web ok")
                    }
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
                
                // 阻止无用资源加载
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
                
                // 阻止CSS和图片加载以提高速度
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
                
                // 注入优化CSS
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
                
                Log.i(TAG, "页面加载完成: ${tvModel.tv.title} (预加载: $preload)")
                
                tvModel.tv.started?.let {
                    webView.evaluateJavascript(it, null)
                }
                
                super.onPageFinished(view, url)
                
                tvModel.tv.script?.let {
                    webView.evaluateJavascript(it, null)
                }
                
                // 执行默认脚本
                val uri = Uri.parse(url)
                val script = scriptMap[uri.host] ?: R.raw.ahtv1
                var scriptContent = context.resources.openRawResource(script)
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
                
                if (preload) {
                    Log.i(TAG, "预加载完成: ${tvModel.tv.title}")
                }
            }
        }
        
        val url = tvModel.videoUrl.value as String
        webView.loadUrl(url)
    }
    
    private fun cleanWebView(webView: WebView) {
        webView.stopLoading()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
    }
    
    fun cleanup() {
        handler.post {
            availableWebViews.clear()
            busyWebViews.clear()
            preloadedWebViews.clear()
        }
    }
}