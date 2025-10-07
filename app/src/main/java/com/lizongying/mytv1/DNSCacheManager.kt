package com.lizongying.mytv1

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DNSCacheManager private constructor() {
    
    companion object {
        private const val TAG = "DNSCacheManager"
        private const val CACHE_EXPIRE_TIME = 5 * 60 * 1000L // 5分钟
        private val PRELOAD_DOMAINS = arrayOf(
            "tv.cctv.com",
            "www.yangshipin.cn",
            "live.kankanews.com",
            "www.ahtv.cn",
            "www.nmtv.cn",
            "live.mgtv.com",
            "www.gdtv.cn",
            "live.jstv.com",
            "www.hebtv.com",
            "live.fjtv.net"
        )
        
        @Volatile
        private var INSTANCE: DNSCacheManager? = null
        
        fun getInstance(): DNSCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DNSCacheManager().also { INSTANCE = it }
            }
        }
    }
    
    private data class DNSEntry(
        val addresses: Array<InetAddress>,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRE_TIME
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as DNSEntry
            
            if (!addresses.contentEquals(other.addresses)) return false
            if (timestamp != other.timestamp) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = addresses.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
    
    private val cache = ConcurrentHashMap<String, DNSEntry>()
    private val executor = Executors.newScheduledThreadPool(2)
    
    init {
        startPreloading()
        startCleanupTask()
    }
    
    fun resolveHost(hostname: String): Array<InetAddress>? {
        // 检查缓存
        cache[hostname]?.let { entry ->
            if (!entry.isExpired()) {
                Log.d(TAG, "DNS缓存命中: $hostname")
                return entry.addresses
            } else {
                cache.remove(hostname)
            }
        }
        
        // 缓存未命中，异步解析并缓存
        try {
            val addresses = InetAddress.getAllByName(hostname)
            cache[hostname] = DNSEntry(addresses, System.currentTimeMillis())
            Log.d(TAG, "DNS解析完成: $hostname -> ${addresses.size} 个地址")
            return addresses
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS解析失败: $hostname", e)
            return null
        }
    }
    
    fun preloadDNS(hostnames: List<String>) {
        executor.submit {
            hostnames.forEach { hostname ->
                try {
                    if (!cache.containsKey(hostname) || cache[hostname]?.isExpired() == true) {
                        val addresses = InetAddress.getAllByName(hostname)
                        cache[hostname] = DNSEntry(addresses, System.currentTimeMillis())
                        Log.d(TAG, "DNS预加载完成: $hostname")
                    }
                } catch (e: UnknownHostException) {
                    Log.w(TAG, "DNS预加载失败: $hostname", e)
                }
            }
        }
    }
    
    private fun startPreloading() {
        // 预加载常用域名
        preloadDNS(PRELOAD_DOMAINS.toList())
        
        // 定期预加载
        executor.scheduleWithFixedDelay({
            preloadDNS(PRELOAD_DOMAINS.toList())
        }, 1, 5, TimeUnit.MINUTES)
    }
    
    private fun startCleanupTask() {
        executor.scheduleAtFixedRate({
            val iterator = cache.entries.iterator()
            var cleanedCount = 0
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.isExpired()) {
                    iterator.remove()
                    cleanedCount++
                }
            }
            if (cleanedCount > 0) {
                Log.d(TAG, "清理过期DNS缓存: $cleanedCount 条")
            }
        }, 10, 10, TimeUnit.MINUTES)
    }
    
    fun getCacheStats(): String {
        return "DNS缓存统计: 总计 ${cache.size} 条记录"
    }
    
    fun clearCache() {
        cache.clear()
        Log.i(TAG, "DNS缓存已清空")
    }
}