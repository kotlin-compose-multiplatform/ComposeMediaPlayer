package io.github.kdroidfilter.composemediaplayer.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object VideoCache {
    private var simpleCache: SimpleCache? = null

    @OptIn(UnstableApi::class)
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L) // 100MB
            val dbProvider = StandaloneDatabaseProvider(context)

            simpleCache = SimpleCache(cacheDir, evictor, dbProvider)
        }
        return simpleCache!!
    }

    fun release() {
        simpleCache?.release()
        simpleCache = null
    }
}

@OptIn(UnstableApi::class)
fun buildCachingDataSourceFactory(context: Context): DataSource.Factory {
    val upstreamFactory = DefaultDataSource.Factory(context)
    return CacheDataSource.Factory()
        .setCache(VideoCache.getCache(context))
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}

