package com.example.aidocumentscanner.scanner

import android.content.Context
import android.util.Log
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

object OpenCVManager {
    
    private const val TAG = "OpenCVManager"
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var initInProgress = false
    
    private var initCallback: ((Boolean) -> Unit)? = null

    fun isReady(): Boolean = isInitialized

    fun initializeAsync(context: Context, onComplete: (Boolean) -> Unit) {
        if (isInitialized) {
            onComplete(true)
            return
        }
        
        if (initInProgress) {
            initCallback = onComplete
            return
        }
        
        initInProgress = true
        initCallback = onComplete
        
        // Use initAsync for production (non-blocking)
        OpenCVLoader.initAsync("4.5.0", context, object : BaseLoaderCallback(context) {
            override fun onManagerConnected(status: Int) {
                isInitialized = (status == LoaderCallbackInterface.SUCCESS)
                initInProgress = false
                
                if (isInitialized) {
                    Log.d(TAG, "OpenCV loaded successfully via initAsync")
                } else {
                    Log.e(TAG, "OpenCV initialization failed with status: $status")
                    // Fallback to initDebug for development
                    fallbackInit()
                }
                
                initCallback?.invoke(isInitialized)
                initCallback = null
            }
        })
    }
    
    private fun fallbackInit() {
        try {
            isInitialized = OpenCVLoader.initDebug()
            if (isInitialized) {
                Log.d(TAG, "OpenCV loaded successfully via initDebug (fallback)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV fallback initDebug failed", e)
            isInitialized = false
        }
    }
}