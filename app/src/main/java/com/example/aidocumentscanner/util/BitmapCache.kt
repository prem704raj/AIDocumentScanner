package com.example.aidocumentscanner.util

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Cache-owned bitmaps are never returned directly to callers.
 * This prevents LruCache eviction from recycling a Bitmap still displayed by Compose.
 */
object BitmapCache {

    private const val MAX_CACHE_MB = 48

    private val cache = object : LruCache<String, Bitmap>(calculateMaxSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (!oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    private fun calculateMaxSizeKb(): Int {
        val runtimeMaxKb = Runtime.getRuntime().maxMemory() / 1024L
        val oneEighthKb = runtimeMaxKb / 8L
        return oneEighthKb
            .coerceAtMost(MAX_CACHE_MB * 1024L)
            .coerceAtLeast(8L * 1024L)
            .toInt()
    }

    /** Returns a caller-owned copy. */
    fun get(key: String): Bitmap? {
        val cached = cache.get(key) ?: return null
        if (cached.isRecycled) {
            cache.remove(key)
            return null
        }
        return cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, true)
    }

    /** Stores a cache-owned copy; the caller keeps ownership of its original bitmap. */
    fun put(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val ownedCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        cache.put(key, ownedCopy)
    }

    /** Removes the cached object and returns a caller-owned copy when possible. */
    fun remove(key: String): Bitmap? {
        val cached = cache.get(key) ?: return null
        val copy = if (!cached.isRecycled) {
            cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, true)
        } else {
            null
        }
        cache.remove(key)
        return copy
    }

    fun clear() = cache.evictAll()
    fun getCurrentSize(): Int = cache.size()
    fun getCacheMaxSize(): Int = cache.maxSize()
    fun getHitCount(): Int = cache.hitCount()
}

fun Bitmap.safeRecycle() {
    if (!isRecycled) recycle()
}

@JvmName("safeRecycleNullable")
fun Bitmap?.safeRecycle() {
    this?.safeRecycle()
}

fun Bitmap.createScaledSafely(
    newWidth: Int,
    newHeight: Int,
    filter: Boolean = true,
    recycleOriginal: Boolean = false
): Bitmap {
    require(newWidth > 0 && newHeight > 0)
    if (width == newWidth && height == newHeight) return this

    val scaled = Bitmap.createScaledBitmap(this, newWidth, newHeight, filter)
    if (recycleOriginal && scaled !== this && !isRecycled) recycle()
    return scaled
}

fun Bitmap.copySafely(
    config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    mutable: Boolean = true
): Bitmap = copy(config, mutable)