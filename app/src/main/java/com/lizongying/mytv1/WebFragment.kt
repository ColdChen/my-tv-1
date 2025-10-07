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
        
        // 释放之前的WebView
        webView?.let { oldWebView ->
            binding.webViewContainer.removeView(oldWebView)
            val oldUrl = this.tvModel?.videoUrl?.value as? String
            if (oldUrl != null) {
                webViewPool?.releaseWebView(oldUrl)
            }
        }
        
        // 从池中获取新的WebView
        webView = webViewPool?.getWebView(tvModel)
        webView?.let { newWebView ->
            newWebView.setOnTouchListener { _, event ->
                if (event != null) {
                    (activity as MainActivity).gestureDetector.onTouchEvent(event)
                }
                true
            }
            
            // 将新WebView添加到容器中
            binding.webViewContainer.addView(newWebView)
            
            // 开始预加载下一个频道
            startPreloading(tvModel)
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