package com.example.aidocumentscanner.scanner

import android.content.Context
import android.util.Log
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

object OpenCVManager {
    private const val TAG = "OpenCVManager"

    @Volatile
    private var initialized = false

    @Volatile
    private var initializationInProgress = false

    private val pendingCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun isReady(): Boolean = initialized

    @Synchronized
    fun initializeAsync(context: Context, onComplete: (Boolean) -> Unit) {
        if (initialized) {
            onComplete(true)
            return
        }

        pendingCallbacks += onComplete
        if (initializationInProgress) return
        initializationInProgress = true

        // The QuickBird dependency bundles OpenCV native libraries. Prefer local initialization so
        // the scanner never depends on a separate OpenCV Manager app being installed.
        val localReady = runCatching { OpenCVLoader.initDebug() }.getOrDefault(false)
        if (localReady) {
            finishInitialization(true)
            return
        }

        val appContext = context.applicationContext
        OpenCVLoader.initAsync("4.5.0", appContext, object : BaseLoaderCallback(appContext) {
            override fun onManagerConnected(status: Int) {
                val success = status == LoaderCallbackInterface.SUCCESS
                if (!success) Log.e(TAG, "OpenCV initialization failed with status=$status")
                finishInitialization(success)
            }
        })
    }

    @Synchronized
    private fun finishInitialization(success: Boolean) {
        initialized = success
        initializationInProgress = false
        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        callbacks.forEach { callback -> runCatching { callback(success) } }
        Log.d(TAG, "OpenCV initialized=$success")
    }
}