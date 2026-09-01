package com.example.aidocumentscanner.util

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Memory-efficient bitmap cache using LruCache
 * Helps prevent OOM crashes by limiting memory usage
 */
object BitmapCache {
    
    private const val DEFAULT_MAX_SIZE_MB = 48 // 48MB default max cache size
    
    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(getMaxSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024 // Size in KB
        }
        
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (!oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }
    
    private fun getMaxSize(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024 // KB
        return (maxMemory / 8).coerceAtMost((DEFAULT_MAX_SIZE_MB * 1024).toLong()).coerceAtLeast((16 * 1024).toLong()).toInt()
    }
    
    fun get(key: String): Bitmap? = cache.get(key)
    
    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(key, bitmap)
        }
    }
    
    fun remove(key: String): Bitmap? = cache.remove(key)
    
    fun clear() {
        cache.evictAll()
    }
    
    fun getCurrentSize(): Int = cache.size()
    
    fun getCacheMaxSize(): Int = cache.maxSize()
    
    fun getHitCount(): Int = cache.hitCount()
}

/**
 * Extension functions for safe bitmap handling
 */
fun Bitmap.safeRecycle() {
    if (!this.isRecycled) {
        this.recycle()
    }
}

@JvmName("safeRecycleNullable")
fun Bitmap?.safeRecycle() {
    this?.safeRecycle()
}

/**
 * Create a scaled bitmap efficiently, recycling the original if requested
 */
fun Bitmap.createScaledSafely(
    newWidth: Int,
    newHeight: Int,
    filter: Boolean = true,
    recycleOriginal: Boolean = false
): Bitmap {
    if (this.width == newWidth && this.height == newHeight) {
        return this
    }
    
    val scaled = Bitmap.createScaledBitmap(this, newWidth, newHeight, filter)
    
    if (recycleOriginal && !this.isRecycled) {
        this.recycle()
    }
    
    return scaled
}

/**
 * Create a copy of bitmap with different config if needed
 */
fun Bitmap.copySafely(config: Bitmap.Config = Bitmap.Config.ARGB_8888, mutable: Boolean = true): Bitmap {
    return this.copy(config, mutable)
}